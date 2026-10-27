/* A bounded, allocation-free record. Only the installed runner writes FD 3.
 * Mapping and DWARF lookup happen in the parent, never in a signal handler. */
#ifndef CODEPOCKET_CPP_FAULT_H
#define CODEPOCKET_CPP_FAULT_H
#include <stdint.h>
#include <stddef.h>
#include <fcntl.h>
#include <signal.h>
#include <unistd.h>
#include <ucontext.h>

struct cpp_fault_record { uint32_t magic, signal, count, reserved; uint64_t addresses[64]; };
_Static_assert(sizeof(struct cpp_fault_record) == 528, "Fault protocol layout");
static int cpp_fault_memory = -1;
static unsigned char cpp_fault_stack[65536] __attribute__((aligned(16)));
static void cpp_fault_handler(int number, siginfo_t *info, void *context) {
    (void)info;
    struct cpp_fault_record record = {.magic=0x43504631, .signal=(uint32_t)number};
#if defined(__aarch64__)
    ucontext_t *state = context;
    uint64_t pc=state->uc_mcontext.pc, lr=state->uc_mcontext.regs[30];
    uint64_t fp=state->uc_mcontext.regs[29], sp=state->uc_mcontext.sp;
    if (pc) record.addresses[record.count++]=pc;
    /* Return addresses identify the calling instruction, not the following line. */
    if (lr >= 4) record.addresses[record.count++]=lr-4;
    while (cpp_fault_memory >= 0 && record.count < 64 && fp >= sp && fp-sp <= 16*1024*1024 && !(fp&15)) {
        uint64_t frame[2];
        if (pread(cpp_fault_memory,frame,sizeof(frame),(off_t)fp) != sizeof(frame)) break;
        if (frame[1] >= 4) record.addresses[record.count++]=frame[1]-4;
        if (frame[0] <= fp) break;
        fp=frame[0];
    }
#else
    (void)context;
#endif
    /* One <= PIPE_BUF write cannot interleave. A failed write must not stop the child. */
    if (write(3,&record,sizeof(record)) == sizeof(record)) (void)kill(getpid(),SIGSTOP);
    /* Parent observes WSTOPPED before SIGCONT, preventing an early-resume race.
     * Preserve the actual signal; the handler does not turn a crash into exit(0). */
    sigset_t signals; sigemptyset(&signals); sigaddset(&signals,number);
    (void)sigprocmask(SIG_UNBLOCK,&signals,NULL);
    (void)kill(getpid(),number);
    _exit(128+number);
}
static int cpp_fault_install(void) {
    if (fcntl(3,F_GETFD) < 0) return 0;
    cpp_fault_memory=open("/proc/self/mem",O_RDONLY|O_CLOEXEC);
    stack_t stack={.ss_sp=cpp_fault_stack,.ss_size=sizeof(cpp_fault_stack),.ss_flags=0};
    if (sigaltstack(&stack,NULL) != 0) return -1;
    struct sigaction action={0};
    action.sa_sigaction=cpp_fault_handler; action.sa_flags=SA_SIGINFO|SA_ONSTACK|SA_RESETHAND;
    sigfillset(&action.sa_mask);
    const int signals[]={SIGSEGV,SIGBUS,SIGILL,SIGTRAP,SIGFPE,SIGABRT,SIGXCPU,SIGXFSZ};
    for (size_t i=0;i<sizeof(signals)/sizeof(signals[0]);++i)
        if (sigaction(signals[i],&action,NULL) != 0) return -1;
    return 0;
}
#endif
