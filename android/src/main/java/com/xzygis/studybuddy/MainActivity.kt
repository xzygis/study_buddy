package com.xzygis.studybuddy

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import com.xzygis.studybuddy.ui.StudyBuddyApp
import com.xzygis.studybuddy.ui.StudyBuddyTheme

class MainActivity : ComponentActivity() {
    private val applicationContainer get() = application as StudyBuddyApplication
    private val plans: PlanViewModel by viewModels {
        PlanViewModel.Factory(
            applicationContainer.repository,
            applicationContainer.alarmScheduler,
        )
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            StudyBuddyTheme {
                StudyBuddyApp(
                    viewModel = plans,
                    onEnablePlan = { planId ->
                        plans.setEnabled(planId, true) {
                            runCatching { applicationContainer.alarmScheduler.showSystemAlarms() }
                        }
                    },
                    onOpenSystemClock = {
                        runCatching { applicationContainer.alarmScheduler.showSystemAlarms() }
                    },
                )
            }
        }
    }

    override fun onResume() {
        super.onResume()
        plans.reconcile()
    }
}
