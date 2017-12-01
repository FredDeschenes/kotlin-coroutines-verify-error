import kotlinx.coroutines.experimental.CoroutineScope
import kotlinx.coroutines.experimental.Deferred
import kotlinx.coroutines.experimental.async
import kotlinx.coroutines.experimental.runBlocking
import org.jdbi.v3.core.Jdbi
import org.jdbi.v3.core.kotlin.withExtensionUnchecked
import org.jdbi.v3.sqlobject.customizer.Bind
import org.jdbi.v3.sqlobject.statement.SqlQuery
import org.jdbi.v3.sqlobject.statement.SqlUpdate
import kotlin.coroutines.experimental.CoroutineContext

data class User(val id: Int, val name: String)

interface MyDao {
    @SqlUpdate("CREATE TABLE user (id INTEGER PRIMARY KEY, name VARCHAR)")
    fun createTable()

    @SqlUpdate("INSERT INTO user(id, name) VALUES (:id, :name)")
    fun insert(@Bind("id") id: Int, @Bind("name") name: String)

    @SqlQuery("SELECT * FROM user ORDER BY id")
    fun listUsers(): List<User>
}

fun main(args: Array<String>) {
    val jdbi = Jdbi.create("jdbc:h2:mem:test;DB_CLOSE_DELAY=-1").apply {
        installPlugins()
    }

    jdbi.withExtensionUnchecked(MyDao::class.java) { dao ->
        dao.createTable()

        dao.insert(1, "Fred")
    }

    regularUsage(jdbi)
    coroutineUsage(jdbi)
}

fun regularUsage(jdbi: Jdbi) {
    val users = jdbi.withExtensionUnchecked(MyDao::class.java) { dao ->
        dao.listUsers()
    }

    println(users)
}

fun coroutineUsage(jdbi: Jdbi) = runBlocking {
    val users = withExtensionAsync(jdbi, MyDao::class.java) { dao ->
        dao.listUsers()
    }

    println(users.await())
}

suspend fun <E, R> CoroutineScope.withExtensionAsync(jdbi: Jdbi,
                                                     type: Class<E>,
                                                     context: CoroutineContext = coroutineContext,
                                                     body: suspend CoroutineScope.(E) -> R): Deferred<R> {
    // Explosion happens somewhere in here
    // FYI:
    //      - 'withExtensionUnchecked' is an inline function
    //      - 'extension' is a proxy of 'MyDao'
    return jdbi.withExtensionUnchecked(type) { extension ->
        async(context) {
            body(extension)
        }
    }
}

