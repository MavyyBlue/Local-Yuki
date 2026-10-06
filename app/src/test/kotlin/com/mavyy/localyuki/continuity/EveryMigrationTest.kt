package com.mavyy.localyuki.continuity

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import com.mavyy.localyuki.brain.BrainRuntime
import com.mavyy.localyuki.foundation.contracts.*
import com.mavyy.localyuki.vault.ContinuityVault
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith
import org.robolectric.ParameterizedRobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import java.io.*
import java.nio.ByteBuffer
import javax.crypto.*
import javax.crypto.spec.*

/** Each shipped schema takes its real adjacent migration path, also through authenticated Vault restore. */
@RunWith(ParameterizedRobolectricTestRunner::class) @Config(sdk=[35])
class EveryMigrationTest(private val version:Int) {
 companion object {
  @JvmStatic @ParameterizedRobolectricTestRunner.Parameters(name="schema {0}") fun versions()=(1..8).map { arrayOf<Any>(it) }
 }
 private lateinit var context:Context
 private val password="owner-history-transfer".toCharArray()
 @Before fun setup(){context=RuntimeEnvironment.getApplication();context.deleteDatabase(ContinuitySchema.NAME)}
 @After fun cleanup(){context.deleteDatabase(ContinuitySchema.NAME)}
 private fun historical() {
  BrainRuntime(context).use { assertTrue(it.open()) }
  val groups=mapOf(9 to listOf("cognitive_plan","executive_audit","body_signal","semantic_vector","presence_delivery","autonomy_policy","life_record","organ_role","organ_receipt","organ_manifest"),
   8 to listOf("model_admission","model_candidate"),7 to listOf("scheduler_state","recovery_intention","recovery_state"),6 to listOf("capability_package","capability_policy"),
   5 to listOf("affect_event","affect_association","affect_state"),4 to listOf("living_recall_event","living_memory_term","living_memory_state","living_memory_metadata"),
   3 to listOf("memory_checkpoint","memory_audit","memory_provenance","memory_revision","durable_memory","memory_evidence","memory_thread","memory_metadata"),
   2 to listOf("state_interaction","state_topic","state_intention","yuki_state"))
  SQLiteDatabase.openDatabase(context.getDatabasePath(ContinuitySchema.NAME).path,null,0).use { db->
   db.execSQL("PRAGMA foreign_keys=OFF")
   for(v in 9 downTo version+1)groups.getValue(v).forEach { db.execSQL("DROP TABLE IF EXISTS $it") }
   if(version<4){db.execSQL("DROP TRIGGER IF EXISTS living_coverage_insert");db.execSQL("DROP TRIGGER IF EXISTS living_coverage_head")}
   db.execSQL("DELETE FROM continuity_migration_history");db.version=version
  }
 }
 @Test fun allAdjacentMigrationsKeepCanonicalContinuityAndAreIdempotent() {
  historical()
  repeat(2) { BrainRuntime(context).use { b->assertTrue("schema $version",b.open());val input=b.saveInput("hello");assertTrue(input is FoundationResult.Success)
   val c=b.continuity.open().reader.read() as FoundationResult.Success;assertEquals("yuki-aster",c.value.self.stableId) } }
  SQLiteDatabase.openDatabase(context.getDatabasePath(ContinuitySchema.NAME).path,null,0).use { db->assertEquals(9,db.version)
   db.rawQuery("SELECT COUNT(*) FROM continuity_migration_history",null).use { it.moveToFirst();assertEquals(9-version,it.getInt(0)) } }
 }
 @Test fun oldVaultRestoresFreshDatabaseAndRequiresNewBodyAuthorization() {
  historical();val bytes=context.getDatabasePath(ContinuitySchema.NAME).readBytes()
  val salt=ByteArray(16){it.toByte()};val nonce=ByteArray(12){(it+16).toByte()}
  val header="YUKIVAULT1".toByteArray()+salt+nonce+ByteBuffer.allocate(4).putInt(version).array()
  val key=SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(PBEKeySpec(password,salt,120000,256)).encoded
  val cipher=Cipher.getInstance("AES/GCM/NoPadding");cipher.init(Cipher.ENCRYPT_MODE,SecretKeySpec(key,"AES"),GCMParameterSpec(128,nonce));cipher.updateAAD(header)
  val archive=header+cipher.doFinal(bytes);key.fill(0);context.deleteDatabase(ContinuitySchema.NAME)
  assertTrue(ContinuityVault(context).restore(ByteArrayInputStream(archive),password) is FoundationResult.Success)
  BrainRuntime(context).use { b->assertTrue(b.open());assertFalse((b.life.policy() as FoundationResult.Success).value.enabled)
   assertTrue((b.capabilities.capabilities() as FoundationResult.Success).value.none { it.enabled }) }
 }
}
