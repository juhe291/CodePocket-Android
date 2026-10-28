package com.codepocket.ide

import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import java.io.IOException
import java.nio.file.Files
import java.nio.file.StandardCopyOption.REPLACE_EXISTING

class ProgramCasesTest {
    @get:Rule val temp=TemporaryFolder()
    private val hash=DocumentTree.digest("actual source context")
    private fun sample(name:String="样例",expected:String="42\n")=ProgramCase(name=name,input="6 7\n",expected=expected)
    private fun result(output:String="42\n",kind:RunKind=RunKind.SUCCESS)=RunResult(output,"",if(kind==RunKind.SUCCESS)"0" else "1",17,engine="test protocol",kind=kind)
    private fun store(directory:File=temp.newFolder())=CaseStore(directory){a,b->Files.move(a.toPath(),b.toPath(),REPLACE_EXISTING);Unit}
    @Test fun comparisonRulesDoNotDiscardSpacesOrChangeInternalLineEndings(){
        assertTrue(OutputComparison.EXACT.matches("42\n","42\n"));assertFalse(OutputComparison.EXACT.matches("42","42\n"))
        val ignore=OutputComparison.IGNORE_FINAL_NEWLINES
        assertTrue(ignore.matches("42\r\n\n","42"));assertFalse(ignore.matches("42 ","42\n"));assertFalse(ignore.matches("a\r\nb","a\nb"))
    }
    @Test fun programOutcomesRemainDistinctAndAnExitFailureCannotPassOnMatchingText(){
        val case=sample()
        assertEquals(CaseVerdict.PASSED,CaseRecord.create(case,hash,result()).verdict)
        assertEquals(CaseVerdict.WRONG_ANSWER,CaseRecord.create(case,hash,result("41\n")).verdict)
        for((kind,verdict) in listOf(RunKind.COMPILE to CaseVerdict.COMPILE_ERROR,RunKind.RUNTIME to CaseVerdict.RUNTIME_ERROR,
            RunKind.TIMEOUT to CaseVerdict.TIMEOUT,RunKind.SERVICE to CaseVerdict.SERVICE_ERROR,RunKind.NETWORK to CaseVerdict.NETWORK_ERROR,RunKind.CANCELLED to CaseVerdict.CANCELLED))
            assertEquals(verdict,CaseRecord.create(case,hash,result(kind=kind)).verdict)
        assertEquals(CaseVerdict.RUNTIME_ERROR,CaseRecord.create(case,hash,result().copy(exitCode="7")).verdict)
        val overflow=CaseRecord.create(case,hash,result("x".repeat(50001)))
        assertEquals(CaseVerdict.OUTPUT_LIMIT,overflow.verdict);assertEquals(50000,overflow.result.stdout.length)
    }
    @Test fun caseAndHistoryRoundTripKeepUnicodeInputsActualOutputAndDiagnosticProof(){
        val directory=temp.newFolder();val store=store(directory);val case=sample("中文 😀").copy(input="空格 \t\u0000\r\n",expected="你好\r\n",comparison=OutputComparison.IGNORE_FINAL_NEWLINES)
        store.saveCases("workspace:file-id",listOf(case))
        val actual=result("你好\n").copy(errorLine=2,errorFile="helper.py",errorSourceHash=hash,sourcePath="main.py",sourceKey="workspace:file-id",sourceHash=hash,projectRoot="/project",workspaceId="workspace")
        val record=CaseRecord.create(case,hash,actual,1234);store.record("workspace:file-id",record)
        assertEquals(CaseFile(listOf(case),listOf(record)),store(directory).load("workspace:file-id"))
        assertEquals(1,directory.listFiles()!!.size)
    }
    @Test fun savedCasesAreIsolatedByStableFileKeysAndEditedCasesDoNotReuseOldVerdicts(){
        val store=store();val a=sample();val b=sample("other")
        store.saveCases("project:a",listOf(a));store.saveCases("project:b",listOf(b))
        val record=CaseRecord.create(a,hash,result());store.record("project:a",record)
        val changed=a.copy(expected="different");store.saveCases("project:a",listOf(changed))
        assertFalse(store.load("project:a").history.single().matches(changed));assertTrue(record.matches(a))
        assertEquals(listOf(b),store.load("project:b").cases);assertTrue(store.load("project:b").history.isEmpty())
        store.saveCases("project:a",emptyList());assertEquals(listOf(record),store.load("project:a").history)
    }
    @Test fun replacementFailurePreservesThePreviousCasesAndHistoryAndCleansOnlyItsTemporaryFile(){
        val dir=temp.newFolder();val case=sample();val original=store(dir);original.saveCases("p:f",listOf(case))
        val record=CaseRecord.create(case,hash,result());original.record("p:f",record)
        val file=dir.listFiles()!!.single();val bytes=file.readBytes()
        val broken=CaseStore(dir){_,_->throw IOException("disk failure")}
        assertThrows(IOException::class.java){broken.saveCases("p:f",emptyList())}
        assertArrayEquals(bytes,file.readBytes());assertEquals(CaseFile(listOf(case),listOf(record)),original.load("p:f"));assertEquals(1,dir.listFiles()!!.size)
    }
    @Test fun corruptionAndInvalidCaseLimitsAreRejectedWithoutOverwritingExistingData(){
        val dir=temp.newFolder();val store=store(dir);val case=sample();store.saveCases("p:f",listOf(case))
        val file=dir.listFiles()!!.single();val original=file.readText();val data=JSONObject(original)
        data.put("payload",data.getString("payload").replace("42","43"));file.writeText(data.toString());val corrupt=file.readBytes()
        assertThrows(IllegalArgumentException::class.java){store.load("p:f")}
        assertThrows(IllegalArgumentException::class.java){store.saveCases("p:f",emptyList())};assertArrayEquals(corrupt,file.readBytes())
        file.writeText(original)
        for(cases in listOf(listOf(case,case),List(21){sample("$it")},listOf(case.copy(input="x".repeat(20001))),List(11){sample("$it",expected="x".repeat(50000))}))
            assertThrows(IllegalArgumentException::class.java){store.saveCases("p:f",cases)}
        assertEquals(listOf(case),store.load("p:f").cases)
    }
    @Test fun historyIsBoundedByBothCountAndTextWhileNewestRecordsRemainComplete(){
        val store=store();val case=sample();store.saveCases("p:f",listOf(case))
        repeat(45){store.record("p:f",CaseRecord.create(case,hash,result(),it.toLong()))}
        assertEquals(40,store.load("p:f").history.size);assertEquals(44L,store.load("p:f").history.first().time)
        val large=CaseRecord.create(case,hash,result("x".repeat(50000)).copy(stderr="y".repeat(50000)),99)
        repeat(10){store.record("p:f",large.copy(id=java.util.UUID.randomUUID().toString(),time=100L+it))}
        val history=store.load("p:f").history
        assertTrue(history.sumOf{it.units}<=CaseStore.MAX_UNITS);assertEquals(109L,history.first().time);assertEquals(50000,history.first().result.stdout.length)
    }
    @Test fun queueAllowsExactlyOneOutstandingCaseAndIgnoresReplaysForeignBatchesAndChangedTickets(){
        val a=sample("a");val b=sample("b");val queue=CaseQueue(listOf(a,b));val first=queue.next()!!
        assertNull(queue.next());assertFalse(queue.accepts(first.copy(case=a.copy(expected="changed"))))
        val other=CaseQueue(listOf(a,b));val foreign=other.next()!!;assertFalse(queue.accepts(foreign))
        assertTrue(queue.complete(first,CaseRecord.create(a,hash,result("wrong"))));assertFalse(queue.complete(first,CaseRecord.create(a,hash,result())))
        val second=queue.next()!!;assertEquals(b,second.case);assertTrue(queue.complete(second,CaseRecord.create(b,hash,result())))
        assertTrue(queue.finished);assertEquals(2,queue.completed);assertNull(queue.next())
    }
    @Test fun stopAcknowledgesTheCurrentResultButNeverLaunchesAnotherCaseAndCompileFailureHaltsTheQueue(){
        val a=sample("a");val b=sample("b");val queue=CaseQueue(listOf(a,b));val active=queue.next()!!
        queue.requestStop();assertTrue(queue.accepts(active));assertNull(queue.next())
        assertTrue(queue.complete(active,CaseRecord.create(a,hash,result(kind=RunKind.CANCELLED))));assertTrue(queue.finished);assertNull(queue.next())
        val compiled=CaseQueue(listOf(a,b));val first=compiled.next()!!
        assertTrue(compiled.complete(first,CaseRecord.create(a,hash,result(kind=RunKind.COMPILE))));assertTrue(compiled.finished);assertEquals(1,compiled.completed)
    }
}
