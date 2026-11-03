package com.codepocket.ide

import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.net.HttpURLConnection
import java.net.SocketTimeoutException
import java.net.URI
import java.net.URL
import java.nio.ByteBuffer
import java.nio.charset.CodingErrorAction
import java.util.concurrent.CancellationException
import java.util.concurrent.atomic.AtomicBoolean

internal data class AppUpdateVersion(val major:Int,val minor:Int,val patch:Int):Comparable<AppUpdateVersion> {
    override fun compareTo(other:AppUpdateVersion)=compareValuesBy(this,other,{it.major},{it.minor},{it.patch})
    override fun toString()="$major.$minor.$patch"
    companion object {
        fun parse(value:String):AppUpdateVersion? {
            if(value.length>96)return null
            val match=Regex("^v?(0|[1-9][0-9]*)[.](0|[1-9][0-9]*)[.](0|[1-9][0-9]*)(?:[+][0-9A-Za-z.-]+)?$").matchEntire(value)?:return null
            val parts=(1..3).map{match.groupValues[it].toIntOrNull()?:return null}
            return AppUpdateVersion(parts[0],parts[1],parts[2])
        }
    }
}

internal data class AppUpdateRepository(val owner:String,val name:String) {
    init {
        require(Regex("[A-Za-z0-9](?:[A-Za-z0-9-]{0,37}[A-Za-z0-9])?").matches(owner))
        require(name.length in 1..100 && name !in setOf(".","..") && Regex("[A-Za-z0-9_.-]+").matches(name))
    }
    val identity get()="$owner/$name"
    val endpoint get()="https://api.github.com/repos/$identity/releases/latest"
    val releasesPage get()="https://github.com/$identity/releases"
    fun trustedPage(value:String,tag:String):String?=trusted(value,"/$identity/releases/tag/$tag")
    fun trustedAsset(value:String,tag:String,filename:String):String?=trusted(value,"/$identity/releases/download/$tag/$filename")
    private fun trusted(value:String,path:String):String?=runCatching {
        val uri=URI(value)
        require(value.length<=4096 && uri.scheme=="https" && uri.host=="github.com" && uri.port==-1 && uri.rawUserInfo==null && uri.rawQuery==null && uri.rawFragment==null)
        require(uri.path==path && '\\' !in value && value.none{it.code<32 || it.code==127})
        uri.toASCIIString()
    }.getOrNull()
    companion object {
        fun parse(value:String):AppUpdateRepository?=runCatching {
            val parts=value.trim().split('/');require(parts.size==2);AppUpdateRepository(parts[0],parts[1])
        }.getOrNull()
    }
}

internal data class AppUpdateAsset(val name:String,val bytes:Long,val url:String)
internal data class AppUpdateRelease(val version:AppUpdateVersion,val tag:String,val page:String,val notes:String,val asset:AppUpdateAsset?)
internal enum class AppUpdateKind { AVAILABLE, CURRENT, AHEAD, NO_RELEASE, UNCONFIGURED, FAILED }
internal enum class AppUpdateFailure { NETWORK, TIMEOUT, RATE_LIMIT, RESPONSE, TOO_LARGE }
internal data class AppUpdateResult(val kind:AppUpdateKind,val release:AppUpdateRelease?=null,val failure:AppUpdateFailure?=null)

/** Read published metadata only. Neither Git workspace credentials nor project files enter this path. */
internal object AppUpdateMetadata {
    fun parse(repository:AppUpdateRepository,current:AppUpdateVersion,text:String):AppUpdateResult {
        val json=JSONObject(text)
        require(json.get("draft")==false && json.get("prerelease")==false)
        val tag=json.getString("tag_name");val version=AppUpdateVersion.parse(tag)?:error("Unsupported release version")
        val page=repository.trustedPage(json.getString("html_url"),tag)?:error("Untrusted release page")
        val notes=if(json.isNull("body"))"" else json.getString("body")
        require(notes.length<=200000)
        val assets=json.getJSONArray("assets");require(assets.length()<=100)
        val candidates=(0 until assets.length()).mapNotNull{index->
            runCatching {
                val asset=assets.getJSONObject(index);val name=asset.getString("name")
                require(name.length<=160 && Regex("CodePocket[-_A-Za-z0-9.]*[.]apk",RegexOption.IGNORE_CASE).matches(name))
                val lower=name.lowercase()
                require(("arm64-v8a" in lower || "universal" in lower) && "x86" !in lower && "armeabi" !in lower)
                require(asset.getString("state")=="uploaded")
                val size=asset.getLong("size");require(size in 1..536870912L)
                val url=repository.trustedAsset(asset.getString("browser_download_url"),tag,name)?:error("Untrusted asset")
                AppUpdateAsset(name,size,url)
            }.getOrNull()
        }
        val exact=candidates.filter{"arm64-v8a" in it.name.lowercase()}
        // A release with ambiguous APKs still has a valid page; never guess between different builds.
        val asset=(if(exact.isNotEmpty())exact else candidates).singleOrNull()
        val kind=when {version>current->AppUpdateKind.AVAILABLE;version==current->AppUpdateKind.CURRENT;else->AppUpdateKind.AHEAD}
        return AppUpdateResult(kind,AppUpdateRelease(version,tag,page,notes,asset))
    }
}

internal interface AppUpdateSource {
    fun check(current:AppUpdateVersion):AppUpdateResult
    fun cancel()
}

/** One bounded, unauthenticated HTTPS GET. Redirects never forward a request to another source. */
internal class GitHubAppUpdateSource(private val repository:AppUpdateRepository,
    private val connect:(URL)->HttpURLConnection={it.openConnection() as HttpURLConnection}):AppUpdateSource {
    private val cancelled=AtomicBoolean()
    @Volatile private var active:HttpURLConnection?=null
    override fun cancel(){
        cancelled.set(true)
        val connection=active ?: return
        // HttpURLConnection.disconnect() may wait on the request lock held by the reading thread.
        // Cancellation is a caller-facing contract, so disconnect from a bounded daemon helper instead.
        Thread({ runCatching { connection.disconnect() } }, "CodePocket-update-cancel").apply {
            isDaemon = true; start()
        }
    }
    override fun check(current:AppUpdateVersion):AppUpdateResult {
        val deadline=System.nanoTime()+20_000_000_000L
        fun alive(){if(cancelled.get() || Thread.currentThread().isInterrupted)throw CancellationException();if(System.nanoTime()>deadline)throw SocketTimeoutException()}
        var connection:HttpURLConnection?=null
        try {
            alive();connection=connect(URL(repository.endpoint));active=connection;alive()
            connection.instanceFollowRedirects=false;connection.connectTimeout=7000;connection.readTimeout=5000
            connection.useCaches=false;connection.requestMethod="GET"
            connection.setRequestProperty("Accept","application/vnd.github+json")
            connection.setRequestProperty("Accept-Encoding","identity")
            connection.setRequestProperty("X-GitHub-Api-Version","2026-03-10")
            connection.setRequestProperty("User-Agent","CodePocket-Android-update-check")
            val status=connection.responseCode;alive()
            if(status==404)return AppUpdateResult(AppUpdateKind.NO_RELEASE)
            if(status==403 || status==429)return AppUpdateResult(AppUpdateKind.FAILED,failure=AppUpdateFailure.RATE_LIMIT)
            if(status!=200)return AppUpdateResult(AppUpdateKind.FAILED,failure=AppUpdateFailure.RESPONSE)
            if(connection.contentLengthLong>MAX_BYTES)return AppUpdateResult(AppUpdateKind.FAILED,failure=AppUpdateFailure.TOO_LARGE)
            require(connection.contentEncoding.isNullOrBlank() || connection.contentEncoding.equals("identity",true))
            val bytes=connection.inputStream.use{input->
                val output=ByteArrayOutputStream();val buffer=ByteArray(8192)
                while(true){alive();val count=input.read(buffer);alive();if(count<0)break
                    if(output.size()+count>MAX_BYTES)return AppUpdateResult(AppUpdateKind.FAILED,failure=AppUpdateFailure.TOO_LARGE)
                    output.write(buffer,0,count)}
                output.toByteArray()
            }
            return try {
                val text=Charsets.UTF_8.newDecoder().onMalformedInput(CodingErrorAction.REPORT).onUnmappableCharacter(CodingErrorAction.REPORT).decode(ByteBuffer.wrap(bytes)).toString()
                AppUpdateMetadata.parse(repository,current,text)
            }catch(_:Exception){AppUpdateResult(AppUpdateKind.FAILED,failure=AppUpdateFailure.RESPONSE)}
        }catch(error:CancellationException){throw error}
        catch(_:SocketTimeoutException){if(cancelled.get())throw CancellationException();return AppUpdateResult(AppUpdateKind.FAILED,failure=AppUpdateFailure.TIMEOUT)}
        catch(_:IllegalArgumentException){if(cancelled.get())throw CancellationException();return AppUpdateResult(AppUpdateKind.FAILED,failure=AppUpdateFailure.RESPONSE)}
        catch(_:org.json.JSONException){if(cancelled.get())throw CancellationException();return AppUpdateResult(AppUpdateKind.FAILED,failure=AppUpdateFailure.RESPONSE)}
        catch(_:Exception){if(cancelled.get())throw CancellationException();return AppUpdateResult(AppUpdateKind.FAILED,failure=AppUpdateFailure.NETWORK)}
        finally{active=null;runCatching{connection?.errorStream?.close()};runCatching{connection?.disconnect()}}
    }
    companion object {private const val MAX_BYTES=262144}
}
