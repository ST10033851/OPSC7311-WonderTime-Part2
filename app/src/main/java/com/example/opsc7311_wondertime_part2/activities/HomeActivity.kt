package com.example.opsc7311_wondertime_part2.activities

import android.annotation.SuppressLint
import android.app.Dialog
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.Button
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import com.example.opsc7311_wondertime_part2.R
import com.example.opsc7311_wondertime_part2.interfaces.updateFirstGoalAchievement
import com.example.opsc7311_wondertime_part2.models.HomeRepository
import com.example.opsc7311_wondertime_part2.models.TimesheetRepository
import com.example.opsc7311_wondertime_part2.models.HomeModel
import com.google.android.material.bottomnavigation.BottomNavigationView
import com.google.android.material.imageview.ShapeableImageView
import com.google.firebase.Firebase
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.DatabaseReference
import com.google.firebase.database.ValueEventListener
import com.google.firebase.database.database
import com.google.firebase.database.ktx.database
import com.google.firebase.storage.FirebaseStorage
import com.google.firebase.storage.StorageReference
import com.squareup.picasso.Picasso
import nl.joery.timerangepicker.TimeRangePicker
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

class HomeActivity : AppCompatActivity() {
    private val timesheetsList = TimesheetRepository.getTimesheetsList()

    var minGoalTime = 0
    var maxGoalTime = 0

    private lateinit var minGoal: TextView
    private lateinit var maxGoal: TextView
    private lateinit var mincheckImage: ImageView
    private lateinit var maxcheckImage: ImageView
    private lateinit var TimepickerBtn: TimeRangePicker
    private lateinit var database: DatabaseReference
    private lateinit var  storageRef: StorageReference
    private lateinit var auth: FirebaseAuth
    private lateinit var databaseRef: DatabaseReference
    private lateinit var profileImageView: ShapeableImageView
    //Droppers. (2021). TimeRangePicker [GitHub Repository]. Retrieved from https://github.com/Droppers/TimeRangePicker

    @SuppressLint("SetTextI18n")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_home)
        val user = FirebaseAuth.getInstance().currentUser!!
        val userId = user.uid
        database = Firebase.database.reference.child("DailyHours").child(userId)

        val bottomNav: BottomNavigationView = findViewById(R.id.bottomNavigationView)
        profileImageView = findViewById(R.id.ProfileImageHome)
        val saveDailyGoalBtn = findViewById<Button>(R.id.saveDailyGoal)
        val minTimeDisplay: TextView = findViewById(R.id.MinTimeDisplay)
        val maxTimeDisplay: TextView = findViewById(R.id.MaxTimeDisplay)
        TimepickerBtn = findViewById(R.id.picker)
        minGoal = findViewById(R.id.MinimumGoalInput)
        maxGoal = findViewById(R.id.MaximumGoalInput)

        val dateFormat = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
        val todayDate = dateFormat.format(Date())

        val currentDate = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }.timeInMillis

        var goalDuration: Int = 0

        var totalDurationToday = 0.0

        database.addListenerForSingleValueEvent(object : ValueEventListener {
            override fun onDataChange(dataSnapshot: DataSnapshot) {
                val dailyGoals = ArrayList<HomeModel>()
                val todayDate = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date())

                for (snapshot in dataSnapshot.children) {
                    val goal = snapshot.getValue(HomeModel::class.java)
                    if (goal?.date == todayDate) {
                        goal?.let {
                            dailyGoals.add(it)
                            totalDurationToday += it.totalHours
                        }
                    }
                }

                if (dailyGoals.isNotEmpty()) {
                    val minimumGoal = dailyGoals.first().minimumGoal
                    val maximumGoal = dailyGoals.first().maximumGoal

                    minTimeDisplay.text = "$minimumGoal Hours"
                    maxTimeDisplay.text = "$maximumGoal Hours"

                    if (totalDurationToday >= minimumGoal) {
                        mincheckImage = findViewById(R.id.minCheckMark)
                        mincheckImage.visibility = View.VISIBLE
                    }

                    if (totalDurationToday >= maximumGoal) {
                        maxcheckImage = findViewById(R.id.maxCheckMark)
                        maxcheckImage.visibility = View.VISIBLE
                    }
                } else {
                    Toast.makeText(this@HomeActivity, "No daily goals for today.", Toast.LENGTH_SHORT).show()
                }
            }

            override fun onCancelled(databaseError: DatabaseError) {
                Toast.makeText(this@HomeActivity, "Failed to retrieve data.", Toast.LENGTH_SHORT).show()
            }
        })

        bottomNav.selectedItemId = R.id.home

        bottomNav.setOnItemSelectedListener { item ->
            when (item.itemId) {
                R.id.home -> true

                R.id.categories -> {
                    handleCategoriesNavigation()
                    true
                }

                R.id.graph -> {
                    handleGraphNavigation()
                    true
                }

                R.id.profile -> {
                    handleProfileNavigation()
                    true
                }

                else -> false
            }

        }
        getProfileImage()

        profileImageView.setOnClickListener(){
            handleProfileNavigation()
        }
        TimepickerBtn.setOnTimeChangeListener(object : TimeRangePicker.OnTimeChangeListener {

            override fun onStartTimeChange(endTime: TimeRangePicker.Time) {
                val timeString = "${endTime.hour}h:${endTime.minute}m"
                maxGoal.text = timeString
                maxGoalTime = endTime.hour
            }

            override fun onEndTimeChange(startTime: TimeRangePicker.Time) {
                val timeString = "${startTime.hour}h:${startTime.minute}m"
                minGoal.text = timeString
                minGoalTime = startTime.hour
            }

            override fun onDurationChange(duration: TimeRangePicker.TimeDuration) {
                goalDuration = maxGoalTime - minGoalTime
            }
        })

        val achievementsSelectorImageView = findViewById<ImageView>(R.id.achievements)
        achievementsSelectorImageView.setOnClickListener {
            handleOtherNavigation()
        }

        saveDailyGoalBtn.setOnClickListener()
        {
            val min = minGoal.text.toString()
            val max = maxGoal.text.toString()

            if (min.isEmpty() || max.isEmpty()) {
                val errorDialog = Dialog(this)
                errorDialog.setContentView(R.layout.error_dialog)
                errorDialog.setCancelable(false)
                val errorMessageTextView = errorDialog.findViewById<TextView>(R.id.ErrorDescription)
                errorMessageTextView.text = "Please enter all fields"
                val dismissButton = errorDialog.findViewById<Button>(R.id.ErrorDone)
                dismissButton.setOnClickListener {
                    errorDialog.dismiss()
                }
                errorDialog.show()
            } else {
                val sharedPreferences = getSharedPreferences("DailyGoalPrefs", Context.MODE_PRIVATE)
                val lastSavedDate = sharedPreferences.getLong("lastSavedDate", 0)

                if (currentDate > lastSavedDate) {
                    showGoalConfirmationDialog()

                    sharedPreferences.edit().putLong("lastSavedDate", currentDate).apply()
                } else {
                    Toast.makeText(
                        this@HomeActivity,
                        "Daily Goal already saved for today!",
                        Toast.LENGTH_SHORT
                    ).show()
                }

            }
        }
    }

    private fun handleProfileNavigation() {
        startActivity(Intent(this, ProfileActivity::class.java))
        overridePendingTransition(R.anim.fade_in, R.anim.fade_out)
    }

    private fun handleGraphNavigation() {
        startActivity(Intent(this, StatisticsActivity::class.java))
        overridePendingTransition(R.anim.fade_in, R.anim.fade_out)
    }

    private fun handleCategoriesNavigation() {
        startActivity(Intent(this, CategoriesActivity::class.java))
        overridePendingTransition(R.anim.fade_in, R.anim.fade_out)
    }

    private fun handleOtherNavigation() {
        startActivity(Intent(this, AchievmentActivity::class.java))
        overridePendingTransition(R.anim.fade_in, R.anim.fade_out)
    }

    private fun getProfileImage()
    {
        storageRef = FirebaseStorage.getInstance().reference.child("ProfileImages")
        auth = FirebaseAuth.getInstance()
        databaseRef = com.google.firebase.ktx.Firebase.database.reference.child("Users").child(auth.currentUser?.uid!!)
        databaseRef.child("profileImageUri").get()
            .addOnSuccessListener{
                val imageUri = it.value as? String
                imageUri?.let { uri ->
                    Picasso.get().load(uri).into(profileImageView)
                }
            }
            .addOnFailureListener{
                Toast.makeText(this, "Failed to load profile image", Toast.LENGTH_SHORT).show()
            }
    }

    private fun showGoalConfirmationDialog() {
        val minGoal = findViewById<TextView>(R.id.MinimumGoalInput)
        val maxGoal = findViewById<TextView>(R.id.MaximumGoalInput)
        val alertDialog = AlertDialog.Builder(this)
        alertDialog.setTitle("Save goals")
        alertDialog.setMessage(
            "Note: You can set your minimum and maximum goals for today. " +
                    "\nAfter saving, you won't be able to enter another goal until tomorrow."
        )
        alertDialog.setPositiveButton("Yes")
        { dialog, _ ->
            saveDailyGoal()
            minGoal.text = ""
            maxGoal.text = ""
            TimepickerBtn.isEnabled = false
            dialog.dismiss()
        }
        alertDialog.setNegativeButton("No")
        { dialog, _ ->
            dialog.dismiss()
        }
        alertDialog.show()
    }

    private fun saveDailyGoal() {
        val minTimeDisplay: TextView = findViewById(R.id.MinTimeDisplay)
        val maxTimeDisplay: TextView = findViewById(R.id.MaxTimeDisplay)
        val user = FirebaseAuth.getInstance().currentUser!!
        val userId = user.uid
        val min = minGoalTime
        val max = maxGoalTime

        val currentDate = Date()
        val formatter = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
        val formattedDate = formatter.format(currentDate)

        val newDailyGoal = HomeModel(min, max, formattedDate, 0)
        HomeRepository.addDailyGoal(newDailyGoal)
        updateFirstGoalAchievement(userId)

        val newCategoryRef = database.push()
        newCategoryRef.setValue(newDailyGoal)

        minTimeDisplay.text = minGoal.text
        maxTimeDisplay.text = maxGoal.text
    }

}
