package com.anitha.remindme.ui

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.widget.ArrayAdapter
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.anitha.remindme.R
import com.anitha.remindme.data.AppDatabase
import com.anitha.remindme.data.TaskEntity
import com.anitha.remindme.data.UserEntity
import com.anitha.remindme.databinding.ActivityMainBinding
import com.anitha.remindme.reminder.ReminderService
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private lateinit var db: AppDatabase
    private var users: List<UserEntity> = emptyList()
    private var selectedUserId: Long? = null
    private val adapter = TaskAdapter(
        onToggleComplete = { task, checked -> toggleComplete(task, checked) },
        onEdit = { task -> showEditTaskDialog(task) }
    )

    private val permissionsLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { perms ->
        val granted = perms.values.all { it }
        if (!granted) {
            Toast.makeText(this, "Some permissions denied. Camera/TTS may not work.", Toast.LENGTH_LONG).show()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)
        db = AppDatabase.get(this)

        binding.recyclerTasks.layoutManager = LinearLayoutManager(this)
        binding.recyclerTasks.adapter = adapter

        binding.btnAddUser.setOnClickListener { showAddUserDialog() }
        binding.btnAddTask.setOnClickListener { addTask() }

        requestNeededPermissions()
        ReminderService.start(this)

        lifecycleScope.launch {
            withContext(Dispatchers.IO) {
                users = db.userDao().getUsers()
                if (users.isEmpty()) {
                    val id = db.userDao().insert(UserEntity(name = "Default"))
                    users = listOf(UserEntity(id = id, name = "Default"))
                }
            }
            updateUsersUI()
        }
    }

    private fun requestNeededPermissions() {
        val permissions = mutableListOf<String>()
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) permissions.add(Manifest.permission.RECORD_AUDIO)
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) permissions.add(Manifest.permission.CAMERA)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            // Foreground service permission declared in manifest; no runtime prompt needed
        }
        if (permissions.isNotEmpty()) permissionsLauncher.launch(permissions.toTypedArray())
    }

    private fun updateUsersUI() {
        val spinner = binding.spinnerUsers
        val names = users.map { it.name }
        spinner.adapter = ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item, names)
        spinner.setSelection(0)
        selectedUserId = users.firstOrNull()?.id
        selectedUserId?.let { observeTasks(it) }
        spinner.setOnItemSelectedListenerCompat { index ->
            selectedUserId = users.getOrNull(index)?.id
            selectedUserId?.let { observeTasks(it) }
        }
    }

    private fun observeTasks(userId: Long) {
        lifecycleScope.launch {
            db.taskDao().observePendingTasks(userId).collect { tasks ->
                adapter.submitList(tasks)
            }
        }
    }

    private fun addTask() {
        val title = binding.editTitle.text.toString().trim()
        val time = binding.editTime.text.toString().trim()
        val userId = selectedUserId
        if (userId == null) {
            Toast.makeText(this, "Please add/select a user", Toast.LENGTH_SHORT).show()
            return
        }
        if (!isValidTime(time)) {
            Toast.makeText(this, "Enter valid time as HH:mm", Toast.LENGTH_SHORT).show()
            return
        }
        if (title.isEmpty()) {
            Toast.makeText(this, "Title cannot be empty", Toast.LENGTH_SHORT).show()
            return
        }
        lifecycleScope.launch(Dispatchers.IO) {
            db.taskDao().insert(TaskEntity(userId = userId, title = title, time = time))
        }
        binding.editTitle.setText("")
        binding.editTime.setText("")
    }

    private fun toggleComplete(task: TaskEntity, checked: Boolean) {
        lifecycleScope.launch(Dispatchers.IO) {
            if (checked) db.taskDao().markCompleted(task.id) else db.taskDao().update(task.copy(completed = false))
        }
    }

    private fun showEditTaskDialog(task: TaskEntity) {
        val view = layoutInflater.inflate(R.layout.dialog_edit_task, null)
        val editTitle = view.findViewById<android.widget.EditText>(R.id.editTitle)
        val editTime = view.findViewById<android.widget.EditText>(R.id.editTime)
        editTitle.setText(task.title)
        editTime.setText(task.time)
        AlertDialog.Builder(this)
            .setTitle("Edit Task")
            .setView(view)
            .setPositiveButton("Save") { _, _ ->
                val newTitle = editTitle.text.toString().trim()
                val newTime = editTime.text.toString().trim()
                if (newTitle.isEmpty() || !isValidTime(newTime)) {
                    Toast.makeText(this, "Invalid input", Toast.LENGTH_SHORT).show()
                    return@setPositiveButton
                }
                lifecycleScope.launch(Dispatchers.IO) {
                    db.taskDao().update(task.copy(title = newTitle, time = newTime))
                }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun showAddUserDialog() {
        val editText = android.widget.EditText(this)
        editText.hint = "User name"
        AlertDialog.Builder(this)
            .setTitle("Add User")
            .setView(editText)
            .setPositiveButton("Add") { _, _ ->
                val name = editText.text.toString().trim()
                if (name.isEmpty()) {
                    Toast.makeText(this, "Name cannot be empty", Toast.LENGTH_SHORT).show()
                    return@setPositiveButton
                }
                lifecycleScope.launch {
                    val newUser = withContext(Dispatchers.IO) {
                        val id = db.userDao().insert(UserEntity(name = name))
                        UserEntity(id = id, name = name)
                    }
                    users = users + newUser
                    updateUsersUI()
                }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun isValidTime(time: String): Boolean {
        val parts = time.split(":")
        if (parts.size != 2) return false
        val h = parts[0].toIntOrNull() ?: return false
        val m = parts[1].toIntOrNull() ?: return false
        return h in 0..23 && m in 0..59
    }
}

private fun androidx.appcompat.widget.AppCompatSpinner.setOnItemSelectedListenerCompat(onSelected: (Int) -> Unit) {
    this.onItemSelectedListener = object : android.widget.AdapterView.OnItemSelectedListener {
        override fun onItemSelected(parent: android.widget.AdapterView<*>?, view: android.view.View?, position: Int, id: Long) {
            onSelected(position)
        }
        override fun onNothingSelected(parent: android.widget.AdapterView<*>?) {}
    }
}
