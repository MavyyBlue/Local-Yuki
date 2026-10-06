package com.mavyy.localyuki.brain
import android.content.Context
import com.mavyy.localyuki.continuity.ContinuitySchema
import com.mavyy.localyuki.foundation.contracts.*
import com.mavyy.localyuki.foundation.memory.*
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import java.nio.*
@RunWith(RobolectricTestRunner::class) @Config(sdk=[35])
class SemanticAuthorityTest {
 private lateinit var context:Context
 private val hash="a".repeat(64)
 private fun <T:Any> ok(r:FoundationResult<T>)=(r as FoundationResult.Success).value
 @Before fun setup(){context=RuntimeEnvironment.getApplication();context.deleteDatabase(ContinuitySchema.NAME)}
 @After fun cleanup(){context.deleteDatabase(ContinuitySchema.NAME)}
 private fun index(m:DurableMemory,vector:FloatArray,model:String=hash){val bytes=ByteBuffer.allocate(vector.size*4).order(ByteOrder.LITTLE_ENDIAN);vector.forEach(bytes::putFloat);BrainDatabase(context).use {db->ok(db.write {it.execSQL("INSERT OR REPLACE INTO semantic_vector VALUES(?,?,?,?)",arrayOf(m.id,m.current.id,model,bytes.array()));true})}}
 @Test fun rankedRecallResolvesOriginalEvidenceAndPersists(){var id="";BrainRuntime(context).use {b->assertTrue(b.open());val m=ok(b.remember("A cat rests among garden flowers"));id=m.id;index(m,floatArrayOf(1f,0f));index(ok(b.remember("A financial market collapsed")),floatArrayOf(0f,1f))}
  BrainRuntime(context).use {b->assertTrue(b.open());val r=ok(b.semantic.resolveVectorRecall(RecallQuery("a quiet feline outside",8),hash,floatArrayOf(1f,0f)));assertEquals(listOf(id),r.candidates.map {it.memory.id});assertEquals(IndexCoverage.PARTIAL,r.coverage);assertTrue(r.candidates.single().memory.current.evidence.all {b.memory.getEvidence(it) is FoundationResult.Success})}}
 @Test fun replacementHashDoesNotReuseOldIndexOrReplaceContinuity(){BrainRuntime(context).use {b->assertTrue(b.open());val m=ok(b.remember("Original continuity"));index(m,floatArrayOf(1f,0f));assertTrue(ok(b.semantic.resolveVectorRecall(RecallQuery("original",8),"b".repeat(64),floatArrayOf(1f,0f))).candidates.isEmpty());assertEquals("Original continuity",ok(b.memory.getCurrent(m.id)).current.content)}}
 @Test fun changedMemoryRevisionNeverReturnsStaleVector(){BrainRuntime(context).use {b->assertTrue(b.open());val m=ok(b.remember("I prefer tea with milk"));index(m,floatArrayOf(1f,0f));ok(b.updateMemory(m,"I now prefer plain tea"));assertTrue(ok(b.semantic.resolveVectorRecall(RecallQuery("tea",8),hash,floatArrayOf(1f,0f))).candidates.isEmpty())}}
 @Test fun nonfiniteOrUnnormalizedDerivedVectorsFailClosed(){BrainRuntime(context).use {b->assertTrue(b.open());index(ok(b.remember("Original authority")),floatArrayOf(Float.NaN,0f));assertTrue(b.semantic.resolveVectorRecall(RecallQuery("authority",8),hash,floatArrayOf(1f,0f)) is FoundationResult.Failure);assertTrue(b.semantic.resolveVectorRecall(RecallQuery("authority",8),hash,floatArrayOf(10f,0f)) is FoundationResult.Failure)}}
}
