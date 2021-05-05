package at.tugraz.onpoint

import androidx.test.espresso.Espresso
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.action.ViewActions
import androidx.test.espresso.assertion.ViewAssertions
import androidx.test.espresso.matcher.ViewMatchers
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.rule.ActivityTestRule
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class SidebarTest {
    @Rule
    @JvmField
    var activityRule: ActivityTestRule<MainTabbedActivity> =
        ActivityTestRule(MainTabbedActivity::class.java)

    /**
     * Checks if sidebar element is displayed
     */
    @Test
    fun checkSidebarDisplayed() {
        onView(ViewMatchers.withId(R.id.drawer)).check(ViewAssertions.matches(ViewMatchers.isDisplayed()))
    }

    /**
     * Checks if sidebar element is clickable
     */
    @Test
    fun checkSidebarClickable() {
        onView(ViewMatchers.withId(R.id.toggle)).perform(ViewActions.click())
    }
}
