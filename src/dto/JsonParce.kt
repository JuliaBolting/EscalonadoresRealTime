package dto

data class JsonParce(
    var simulation_time: Int = 0,
    var scheduler_name: String = "",
    var tasks_number: Int = 0,
    var tasks: ArrayList<TaskDTO> = ArrayList(),
)