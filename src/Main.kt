import com.google.gson.Gson
import dto.JsonParce
import gui.Escalonadores
import java.io.BufferedReader
import java.io.FileInputStream
import java.io.InputStreamReader

fun main() {

    val pathArquivoJson = FileInputStream("script/json_test.json")
    val lerArquivo = BufferedReader(InputStreamReader(pathArquivoJson))
    val obJson: JsonParce = Gson().fromJson(lerArquivo, JsonParce::class.java)

    println("Entrada do json: ")
    println("tasks_number: " + obJson.tasks_number)
    println("scheduler_name: " + obJson.scheduler_name)
    println("simulation_time: " + obJson.simulation_time)
    println("tasks: ")
    for ((i, task) in obJson.tasks.withIndex()) {
        println()
        task.id_task = i
        task.priority = 0
        println("id_task: " + task.id_task)
        println("offset: " + task.offset)
        println("period_time: " + task.period_time)
        println("computation_time: " + task.computation_time)
        println("quantum: " + task.quantum)
        println("deadline: " + task.deadline)
        println()
    }

    val escalonador = Escalonadores(obJson)
    escalonador.escalonador()

}