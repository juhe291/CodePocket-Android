package com.codepocket.ide

import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream
import java.util.UUID

enum class OutputComparison(val label:String){
    EXACT("逐字比较"),IGNORE_FINAL_NEWLINES("仅忽略末尾换行");
    fun matches(expected:String,actual:String)=when(this){
        EXACT->expected==actual
        IGNORE_FINAL_NEWLINES->expected.trimEnd('\r','\n')==actual.trimEnd('\r','\n')
    }
}
data class ProgramCase(val id:String=UUID.randomUUID().toString(),val name:String,val input:String,val expected:String,
    val comparison:OutputComparison=OutputComparison.EXACT){
    fun validate(){
        require(id.matches(Regex("[0-9a-f]{8}(-[0-9a-f]{4}){3}-[0-9a-f]{12}")))
        require(name.isNotBlank()&&name.length<=80&&name.none{it.isISOControl()}){"用例名称最多 80 字且不能包含控制字符"}
        require(input.length<=20000&&expected.length<=50000){"输入最多 2 万字符，预期输出最多 5 万字符"}
    }
    fun fingerprint()=DocumentTree.digest(json().toString())
    internal fun json()=JSONObject().put("id",id).put("name",name).put("input",input).put("expected",expected).put("comparison",comparison.name)
    companion object{
        internal fun read(data:JSONObject)=ProgramCase(data.getString("id"),data.getString("name"),data.getString("input"),data.getString("expected"),OutputComparison.valueOf(data.getString("comparison"))).also{it.validate()}
    }
}
enum class CaseVerdict(val label:String){
    PASSED("通过"),WRONG_ANSWER("答案错误"),COMPILE_ERROR("编译错误"),RUNTIME_ERROR("运行错误"),TIMEOUT("超时"),
    SERVICE_ERROR("服务错误"),NETWORK_ERROR("网络错误"),CANCELLED("已中止"),OUTPUT_LIMIT("输出超限")
}
data class CaseRecord(val id:String,val case:ProgramCase,val contextHash:String,val time:Long,val verdict:CaseVerdict,val result:RunResult){
    val units get()=case.name.length+case.input.length+case.expected.length+result.stdout.length+result.stderr.length
    fun matches(current:ProgramCase)=case.fingerprint()==current.fingerprint()
    fun validate(){
        case.validate();require(id.matches(Regex("[0-9a-f]{8}(-[0-9a-f]{4}){3}-[0-9a-f]{12}")))
        require(contextHash.matches(Regex("[0-9a-f]{64}"))&&time>=0&&result.elapsedMs>=0&&result.stdout.length<=50000&&result.stderr.length<=50000)
    }
    internal fun json()=JSONObject().put("id",id).put("case",case.json()).put("context",contextHash).put("time",time).put("verdict",verdict.name)
        .put("result",JSONObject().put("stdout",result.stdout).put("stderr",result.stderr).put("exit",result.exitCode)
            .put("elapsed",result.elapsedMs).put("kind",result.kind.name).put("engine",result.engine).put("line",result.errorLine)
            .put("file",result.errorFile).put("sourcePath",result.sourcePath).put("projectRoot",result.projectRoot)
            .put("workspaceId",result.workspaceId).put("sourceKey",result.sourceKey).put("sourceHash",result.sourceHash).put("errorHash",result.errorSourceHash))
    companion object{
        fun create(case:ProgramCase,contextHash:String,result:RunResult,time:Long=System.currentTimeMillis()):CaseRecord{
            case.validate();require(contextHash.matches(Regex("[0-9a-f]{64}"))&&time>=0)
            val verdict=when(result.kind){
                RunKind.CANCELLED->CaseVerdict.CANCELLED
                RunKind.TIMEOUT->CaseVerdict.TIMEOUT
                RunKind.COMPILE->CaseVerdict.COMPILE_ERROR
                RunKind.RUNTIME->CaseVerdict.RUNTIME_ERROR
                RunKind.SERVICE->CaseVerdict.SERVICE_ERROR
                RunKind.NETWORK->CaseVerdict.NETWORK_ERROR
                RunKind.SUCCESS->when{
                    result.exitCode!="0"->CaseVerdict.RUNTIME_ERROR
                    result.stdout.length>50000||result.stderr.length>50000->CaseVerdict.OUTPUT_LIMIT
                    case.comparison.matches(case.expected,result.stdout)->CaseVerdict.PASSED
                    else->CaseVerdict.WRONG_ANSWER
                }
            }
            return CaseRecord(UUID.randomUUID().toString(),case,contextHash,time,verdict,
                result.copy(stdout=result.stdout.take(50000),stderr=result.stderr.take(50000)))
        }
        internal fun read(data:JSONObject):CaseRecord{
            val r=data.getJSONObject("result")
            fun text(key:String)=if(r.isNull(key))null else r.getString(key)
            val result=RunResult(r.getString("stdout"),r.getString("stderr"),r.getString("exit"),r.getLong("elapsed"),
                if(r.isNull("line"))null else r.getInt("line"),r.getString("engine"),RunKind.valueOf(r.getString("kind")),
                text("file"),text("sourcePath"),text("projectRoot"),text("workspaceId"),text("sourceKey"),text("sourceHash"),text("errorHash"))
            val record=CaseRecord(data.getString("id"),ProgramCase.read(data.getJSONObject("case")),data.getString("context"),data.getLong("time"),CaseVerdict.valueOf(data.getString("verdict")),result)
            record.validate()
            return record
        }
    }
}
data class CaseFile(val cases:List<ProgramCase> = emptyList(),val history:List<CaseRecord> = emptyList())

/** Call on WorkspaceIo. Stable file-session keys survive in-app rename/move, never become paths. */
class CaseStore(private val directory:File,private val replace:(File,File)->Unit){
    companion object{const val MAX_CASES=20;const val MAX_HISTORY=40;const val MAX_UNITS=500000;private const val MAX_BYTES=8_000_000L}
    private fun location(key:String):File{
        require(key.isNotBlank()&&key.length<=512)
        require(directory.canonicalFile==directory.absoluteFile&&(directory.isDirectory||directory.mkdirs())){"无法创建用例目录"}
        return File(directory,DocumentTree.digest(key)+".json").also{require(it.canonicalFile==it)}
    }
    @Synchronized fun load(key:String):CaseFile{
        val file=location(key);if(!file.exists())return CaseFile()
        require(file.isFile&&file.length()<=MAX_BYTES){"用例记录过大或损坏"}
        val text=file.inputStream().use{Workspace.utf8Reader(it).use{r->Workspace.readLimited(r,MAX_BYTES.toInt())}}
        val data=JSONObject(text);require(data.getInt("version")==1&&data.getString("key")==key)
        val payload=data.getString("payload");require(DocumentTree.digest(payload)==data.getString("sha256")){"用例记录校验失败，原文件已保留"}
        val body=JSONObject(payload);val cases=body.getJSONArray("cases");val history=body.getJSONArray("history")
        require(cases.length()<=MAX_CASES&&history.length()<=MAX_HISTORY)
        return CaseFile((0 until cases.length()).map{ProgramCase.read(cases.getJSONObject(it))},
            (0 until history.length()).map{CaseRecord.read(history.getJSONObject(it))}).also(::validate)
    }
    private fun validate(value:CaseFile){
        require(value.cases.size<=MAX_CASES&&value.history.size<=MAX_HISTORY)
        value.cases.forEach{it.validate()};require(value.cases.map{it.id}.distinct().size==value.cases.size)
        value.history.forEach{it.validate()}
        require(value.history.map{it.id}.distinct().size==value.history.size)
        require(value.cases.sumOf{it.input.length+it.expected.length+it.name.length}<=MAX_UNITS){"用例内容合计超过 50 万字符"}
        require(value.history.sumOf{it.units}<=MAX_UNITS)
    }
    private fun write(key:String,value:CaseFile):CaseFile{
        validate(value);val target=location(key)
        val body=JSONObject().put("cases",JSONArray().apply{value.cases.forEach{put(it.json())}})
            .put("history",JSONArray().apply{value.history.forEach{put(it.json())}}).toString()
        val data=JSONObject().put("version",1).put("key",key).put("payload",body).put("sha256",DocumentTree.digest(body)).toString().toByteArray(Charsets.UTF_8)
        require(data.size<=MAX_BYTES)
        val temporary=File.createTempFile(".case-",".tmp",directory)
        try{FileOutputStream(temporary).use{it.write(data);it.fd.sync()};replace(temporary,target)}finally{temporary.delete()}
        return value
    }
    @Synchronized fun saveCases(key:String,cases:List<ProgramCase>):CaseFile=write(key,load(key).copy(cases=cases.toList()))
    @Synchronized fun record(key:String,record:CaseRecord):CaseFile{
        val previous=load(key);val history=(listOf(record)+previous.history).take(MAX_HISTORY).toMutableList()
        while(history.sumOf{it.units}>MAX_UNITS)history.removeAt(history.lastIndex)
        return write(key,previous.copy(history=history))
    }
}

/** Pure ordering/identity state; adapters launch the real service and acknowledge only after persistence. */
internal class CaseQueue(cases:List<ProgramCase>){
    val cases=cases.toList();val batchId=UUID.randomUUID().toString();private var index=0;private var sequence=0L;private var awaiting:Long?=null
    private var stopRequested=false
    var stopped=false;private set
    init{require(cases.isNotEmpty()&&cases.size<=CaseStore.MAX_CASES&&cases.map{it.id}.distinct().size==cases.size);cases.forEach{it.validate()}}
    data class Ticket(val batchId:String,val token:Long,val index:Int,val case:ProgramCase)
    fun next():Ticket?{
        if(stopped||stopRequested||index>=cases.size||awaiting!=null)return null
        return Ticket(batchId,++sequence,index,cases[index]).also{awaiting=it.token}
    }
    fun accepts(ticket:Ticket)=!stopped&&ticket.batchId==batchId&&awaiting==ticket.token&&index==ticket.index&&cases[index]==ticket.case
    fun complete(ticket:Ticket,record:CaseRecord):Boolean{
        if(!accepts(ticket)||!record.matches(ticket.case))return false
        awaiting=null;index++
        if(stopRequested||record.verdict in setOf(CaseVerdict.CANCELLED,CaseVerdict.SERVICE_ERROR,CaseVerdict.NETWORK_ERROR,CaseVerdict.COMPILE_ERROR))stopped=true
        return true
    }
    fun stop(){stopped=true;awaiting=null}
    fun requestStop(){stopRequested=true;if(awaiting==null)stopped=true}
    val finished get()=stopped||index>=cases.size
    val completed get()=index
}
