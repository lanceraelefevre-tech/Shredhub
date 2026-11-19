package com.example.data

import android.content.Context
import com.example.R
import com.example.model.PresetCategory
import com.example.model.RigConfig
import com.example.model.RigPresetDoc
import com.google.firebase.Firebase
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.auth
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreException
import com.google.firebase.firestore.Query
import com.google.firebase.firestore.snapshots
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.tasks.await
import java.util.UUID

class PresetRepository(
    private val db: FirebaseFirestore,
    private val auth: FirebaseAuth = Firebase.auth,
) {
    constructor(context: Context) : this(
        FirebaseFirestore.getInstance(
            context.applicationContext.getString(R.string.firestore_database_id)
        )
    )

    private fun requireUserId(expectedUid: String? = null): String {
        val uid = auth.currentUser?.uid
            ?: throw IllegalStateException("User must be signed in with Google before accessing presets.")
        if (expectedUid != null) {
            check(uid == expectedUid) { "Signed-in user changed; refusing cross-user operation." }
        }
        return uid
    }

    fun observePresets(userId: String): Flow<List<RigPresetDoc>> = flow {
        val uid = requireUserId(userId)
        val path = "users/$uid/presets"
        emitAll(
            db.collection("users")
                .document(uid)
                .collection("presets")
                .orderBy("updatedAt", Query.Direction.DESCENDING)
                .snapshots()
                .map { snapshot ->
                    snapshot.toObjects(
                        RigPresetDoc::class.java,
                        DocumentSnapshot.ServerTimestampBehavior.ESTIMATE,
                    )
                }
                .catch { error ->
                    val firestoreError = generateSequence(error) { it.cause }
                        .filterIsInstance<FirebaseFirestoreException>()
                        .firstOrNull()
                    if (firestoreError == null && error is CancellationException) throw error
                    val exceptionToLog = firestoreError ?: (error as? Exception)
                    if (exceptionToLog != null && auth.currentUser?.uid == uid) {
                        handleFirestoreError(exceptionToLog, OperationType.LIST, path)
                    }
                    throw error
                }
        )
    }

    suspend fun createPreset(
        userId: String,
        name: String,
        category: PresetCategory,
        favorite: Boolean,
        config: RigConfig,
    ): Result<String> {
        val trimmedName = name.trim()
        if (trimmedName.isEmpty() || trimmedName.length > 50) {
            return Result.failure(IllegalArgumentException("Preset name must be 1 to 50 characters."))
        }
        val presetId = "preset_${UUID.randomUUID().toString().replace("-", "").take(20)}"
        val path = "users/$userId/presets/$presetId"
        return try {
            val uid = requireUserId(userId)
            val doc = RigPresetDoc.fromRigConfig(
                userId = uid,
                name = trimmedName,
                category = category,
                favorite = favorite,
                config = config,
            )
            db.collection("users")
                .document(uid)
                .collection("presets")
                .document(presetId)
                .set(doc.toCreateMap())
                .await()
            Result.success(presetId)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.CREATE, path)
            Result.failure(e)
        }
    }

    suspend fun updatePreset(
        userId: String,
        presetId: String,
        name: String,
        category: PresetCategory,
        favorite: Boolean,
        config: RigConfig,
    ): Result<Unit> {
        val trimmedName = name.trim()
        if (trimmedName.isEmpty() || trimmedName.length > 50 || presetId.isBlank()) {
            return Result.failure(IllegalArgumentException("Invalid preset ID or name."))
        }
        val path = "users/$userId/presets/$presetId"
        return try {
            val uid = requireUserId(userId)
            val doc = RigPresetDoc.fromRigConfig(
                userId = uid,
                name = trimmedName,
                category = category,
                favorite = favorite,
                config = config,
                existingId = presetId,
            )
            db.collection("users")
                .document(uid)
                .collection("presets")
                .document(presetId)
                .update(doc.toUpdateMap())
                .await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, path)
            Result.failure(e)
        }
    }

    suspend fun toggleFavorite(
        userId: String,
        presetId: String,
        favorite: Boolean,
    ): Result<Unit> {
        if (presetId.isBlank()) {
            return Result.failure(IllegalArgumentException("Preset ID cannot be blank."))
        }
        val path = "users/$userId/presets/$presetId"
        return try {
            val uid = requireUserId(userId)
            db.collection("users")
                .document(uid)
                .collection("presets")
                .document(presetId)
                .update(
                    mapOf(
                        "favorite" to favorite,
                        "updatedAt" to FieldValue.serverTimestamp(),
                    )
                )
                .await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, path)
            Result.failure(e)
        }
    }

    suspend fun deletePreset(
        userId: String,
        presetId: String,
    ): Result<Unit> {
        if (presetId.isBlank()) {
            return Result.failure(IllegalArgumentException("Preset ID cannot be blank."))
        }
        val path = "users/$userId/presets/$presetId"
        return try {
            val uid = requireUserId(userId)
            db.collection("users")
                .document(uid)
                .collection("presets")
                .document(presetId)
                .delete()
                .await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.DELETE, path)
            Result.failure(e)
        }
    }
}
