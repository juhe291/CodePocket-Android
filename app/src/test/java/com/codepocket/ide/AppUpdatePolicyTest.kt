package com.codepocket.ide

import org.json.JSONArray
import org.json.JSONObject
import org.junit.Test
import org.junit.Assert.*

internal object AppUpdateFixtures {
    val repository=AppUpdateRepository("fixture","CodePocket-Android")
    val current=AppUpdateVersion(0,2,1)
    fun asset(tag:String="v0.2.2",name:String="CodePocket-v0.2.2-arm64-v8a-debug.apk")=JSONObject()
        .put("name",name).put("size",123000000).put("state","uploaded")
        .put("browser_download_url","https://github.com/${repository.identity}/releases/download/$tag/$name")
    fun release(tag:String="v0.2.2",assets:JSONArray=JSONArray().put(asset(tag)))=JSONObject()
        .put("draft",false).put("prerelease",false).put("tag_name",tag)
        .put("html_url","https://github.com/${repository.identity}/releases/tag/$tag")
        .put("body","# 实际更新说明\r\n\r\n保留中文与 emoji 😀\n最后一行。").put("assets",assets)
}

class AppUpdatePolicyTest {
    private val repo=AppUpdateFixtures.repository
    private fun parse(json:JSONObject,current:AppUpdateVersion=AppUpdateFixtures.current)=AppUpdateMetadata.parse(repo,current,json.toString())
    @Test fun numericVersionsDoNotUseLexicalOrderingAndIgnoreBuildMetadata(){
        assertTrue(AppUpdateVersion.parse("v0.10.0")!!>AppUpdateVersion.parse("0.9.99")!!)
        assertEquals(AppUpdateFixtures.current,AppUpdateVersion.parse("0.2.1+host.3"))
        for(value in listOf("1.2","1.02.3","-1.2.3","1.2.3-rc.1","1.2.3\n","2147483648.0.0","../1.2.3"))assertNull(value,AppUpdateVersion.parse(value))
    }
    @Test fun sourcesAndReturnedLinksRemainInTheConfiguredHttpsRepository(){
        assertEquals("https://api.github.com/repos/fixture/CodePocket-Android/releases/latest",repo.endpoint)
        for(value in listOf("","https://github.com/fixture/repo","fixture/../repo","fixture/repo?x","fixture/repo/extra"))assertNull(AppUpdateRepository.parse(value))
        val tag="v0.2.2"
        for(url in listOf("http://github.com/${repo.identity}/releases/tag/$tag","https://github.com.evil.test/${repo.identity}/releases/tag/$tag",
            "https://token@github.com/${repo.identity}/releases/tag/$tag","https://github.com:444/${repo.identity}/releases/tag/$tag",
            "https://github.com/another/repo/releases/tag/$tag","https://github.com/${repo.identity}/releases/tag/$tag?token=private"))assertNull(url,repo.trustedPage(url,tag))
    }
    @Test fun publishedMetadataRetainsAllNotesAndTheActualCompatibleAsset(){
        val json=AppUpdateFixtures.release();val result=parse(json)
        assertEquals(AppUpdateKind.AVAILABLE,result.kind);assertEquals(AppUpdateVersion(0,2,2),result.release!!.version)
        assertEquals(json.getString("body"),result.release.notes);assertEquals(123000000L,result.release.asset!!.bytes)
        assertEquals(AppUpdateFixtures.asset().getString("browser_download_url"),result.release.asset.url)
    }
    @Test fun equalityAndDevelopmentVersionsNeverOfferADowngrade(){
        assertEquals(AppUpdateKind.CURRENT,parse(AppUpdateFixtures.release("v0.2.1")).kind)
        assertEquals(AppUpdateKind.AHEAD,parse(AppUpdateFixtures.release(),AppUpdateVersion(0,3,0)).kind)
    }
    @Test fun draftsPrereleasesMalformedVersionsAndExternalPagesAreRejected(){
        for(json in listOf(AppUpdateFixtures.release().put("draft",true),AppUpdateFixtures.release().put("prerelease",true),
            AppUpdateFixtures.release("v0.2.2-rc.1"),AppUpdateFixtures.release().put("html_url","https://example.org/download")))
            assertThrows(Exception::class.java){parse(json)}
    }
    @Test fun missingOrWrongArchitectureAssetsStillHaveAnHonestReleasePage(){
        val foreign=AppUpdateFixtures.asset().put("browser_download_url","https://example.org/other.apk")
        val assets=JSONArray().put(foreign).put(AppUpdateFixtures.asset(name="CodePocket-x86_64.apk"))
            .put(AppUpdateFixtures.asset(name="CodePocket-arm64-v8a.apk").put("state","new"))
        val result=parse(AppUpdateFixtures.release(assets=assets));assertEquals(AppUpdateKind.AVAILABLE,result.kind)
        assertNull(result.release!!.asset);assertEquals(repo.trustedPage(result.release.page,"v0.2.2"),result.release.page)
    }
    @Test fun multipleBuildsAreNotGuessedAndAnExactArm64AssetBeatsUniversal(){
        val two=JSONArray().put(AppUpdateFixtures.asset()).put(AppUpdateFixtures.asset(name="CodePocket-arm64-v8a-release.apk"))
        assertNull(parse(AppUpdateFixtures.release(assets=two)).release!!.asset)
        val preferred=JSONArray().put(AppUpdateFixtures.asset(name="CodePocket-universal.apk")).put(AppUpdateFixtures.asset())
        assertEquals(AppUpdateFixtures.asset().getString("name"),parse(AppUpdateFixtures.release(assets=preferred)).release!!.asset!!.name)
    }
    @Test fun oversizedMetadataAndInvalidAssetSizesCannotMasqueradeAsDownloads(){
        assertThrows(Exception::class.java){parse(AppUpdateFixtures.release().put("body","a".repeat(200001)))}
        val result=parse(AppUpdateFixtures.release(assets=JSONArray().put(AppUpdateFixtures.asset().put("size",-1))))
        assertNull(result.release!!.asset)
        val tooMany=JSONArray();repeat(101){tooMany.put(AppUpdateFixtures.asset())}
        assertThrows(Exception::class.java){parse(AppUpdateFixtures.release(assets=tooMany))}
    }
}
