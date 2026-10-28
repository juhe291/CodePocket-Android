package com.codepocket.ide

import android.content.Intent
import android.os.*
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows
import org.robolectric.annotation.Config
import org.robolectric.annotation.LooperMode
import java.io.File
import java.util.UUID
import java.util.concurrent.CopyOnWriteArrayList

/** Actual Messenger rejects invalid metadata before Chaquopy or project code is entered. */
@RunWith(RobolectricTestRunner::class) @Config(sdk=[34]) @LooperMode(LooperMode.Mode.PAUSED)
class PythonRunServiceContractTest {
    private fun reject(rootKind:String,manifest:String){
        val controller=Robolectric.buildService(PythonExecutionService::class.java).create();val service=controller.get()
        val root=File(service.filesDir.canonicalFile,"workspace").apply{mkdirs()}
        val entry=File(root,"main.py").apply{writeText("open('must-not-run','w').write('bad')")}
        val directory=File(service.cacheDir.canonicalFile,"python-runs/${UUID.randomUUID()}").apply{mkdirs()}
        val metadata=File(directory,"overlays.json").apply{writeText(manifest)}
        val results=CopyOnWriteArrayList<Bundle>()
        try{
            val reply=Messenger(Handler(Looper.getMainLooper()){m->if(m.what==PythonExecutionService.RESULT)results.add(m.data);true})
            Messenger(service.onBind(Intent())).send(Message.obtain(null,PythonExecutionService.RUN).apply{
                replyTo=reply;data=Bundle().apply{
                    putLong("id",31);putString("code",entry.readText());putString("stdin","");putString("path",entry.path)
                    putString("root",if(rootKind=="foreign")root.parentFile!!.path else root.path);putString("pythonJob",directory.path)
                }
            })
            repeat(500){Shadows.shadowOf(Looper.getMainLooper()).idle();if(results.isNotEmpty())return@repeat;Thread.sleep(2)}
            assertEquals(1,results.size);val result=runResultFromBundle(results.single())
            assertEquals(31L,results.single().getLong("id"));assertEquals(RunKind.RUNTIME,result.kind)
            assertTrue(result.stderr.startsWith("Python 执行失败："));assertFalse(File(root,"must-not-run").exists())
            assertTrue(metadata.isFile);assertEquals("open('must-not-run','w').write('bad')",entry.readText())
        }finally{controller.destroy()}
    }
    @Test fun invalidOverlayTraversalNeverExecutesProjectCode(){reject("internal","{\"version\":1,\"files\":{\"../escape.py\":\"bad\"}}")}
    @Test fun aJobCannotChooseAnUnownedExecutionDirectory(){reject("foreign","{\"version\":1,\"files\":{}}")}
}
