package org.jetbrains.demo.user

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.jetbrains.exposed.v1.core.Column
import org.jetbrains.exposed.v1.core.ResultRow
import org.jetbrains.exposed.v1.core.SqlExpressionBuilder.eq
import org.jetbrains.exposed.v1.core.dao.id.LongIdTable
import org.jetbrains.exposed.v1.jdbc.Database
import org.jetbrains.exposed.v1.jdbc.deleteWhere
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import org.jetbrains.exposed.v1.jdbc.updateReturning
import org.jetbrains.exposed.v1.jdbc.upsertReturning

object UserTable : LongIdTable("users", "user_id") {
    val subject = varchar("subject", 256).uniqueIndex()
    val email = varchar("email", 256).nullable()
    val displayName = varchar("display_name", 256).nullable()
    val aboutMe = varchar("about_me", 256).nullable()
}


interface UserRepository {
    suspend fun create(subject: String): User
    suspend fun findOrNull(subject: String?): User?
    suspend fun findAll(): List<User>
    suspend fun findByEmail(email: String): User?
    suspend fun create(subj: String, updateUser: UpdateUser): User
    suspend fun delete(subj: String): Boolean
}

class ExposedUserRepository(private val database: Database) : UserRepository {
    override suspend fun create(subject: String): User =
        withContext(Dispatchers.IO) {
            transaction(database) {
                UserTable.upsertReturning(
                    keys = arrayOf(UserTable.subject)
                ) { upsert ->
                    upsert[UserTable.subject] = subject
                }.single().toUser()
            }
        }

    override suspend fun findOrNull(subject: String?): User? =
        if (subject == null) null
        else withContext(Dispatchers.IO) {
            transaction(database) {
                UserTable.selectAll().where { UserTable.subject eq subject }.singleOrNull()?.toUser()
            }
        }

    override suspend fun findAll(): List<User> = withContext(Dispatchers.IO) {
        transaction(database) {
            UserTable.selectAll().map { it.toUser() }
        }
    }

    override suspend fun findByEmail(email: String): User? = withContext(Dispatchers.IO) {
        transaction(database) {
            UserTable.selectAll().where { UserTable.email eq email }.singleOrNull()?.toUser()
        }
    }

    override suspend fun create(subj: String, updateUser: UpdateUser): User = withContext(Dispatchers.IO) {
        transaction(database) {
            UserTable.updateReturning(where = { UserTable.subject eq subj }) { upsert ->
                if (updateUser.email != null) upsert[email] = updateUser.email
                if (updateUser.aboutMe != null) upsert[aboutMe] = updateUser.aboutMe
                if (updateUser.displayName != null) upsert[displayName] = updateUser.displayName
            }.single().toUser()
        }
    }

    override suspend fun delete(subj: String) = withContext(Dispatchers.IO) {
        transaction(database) {
            UserTable.deleteWhere { UserTable.subject eq subj } == 1
        }
    }

    private fun ResultRow.toUser(): User = User(
        this[UserTable.id].value,
        this[UserTable.subject],
        this[UserTable.email],
        this[UserTable.displayName],
        this[UserTable.aboutMe],
    )
}