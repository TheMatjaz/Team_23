@file:Suppress("unused")

package at.tugraz.onpoint.ui.main

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.SearchView
import android.widget.TextView
import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentManager
import androidx.lifecycle.ViewModelProvider
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey
import at.tugraz.onpoint.R
import at.tugraz.onpoint.database.AssignmentDao
import at.tugraz.onpoint.database.MoodleDao
import at.tugraz.onpoint.database.OnPointAppDatabase
import at.tugraz.onpoint.database.getDbInstance
import at.tugraz.onpoint.moodle.*
import com.google.android.material.floatingactionbutton.FloatingActionButton
import java.net.URL
import java.util.*
import kotlin.collections.ArrayList


class AssignmentsTabFragment : Fragment() {
    private lateinit var pageViewModel: PageViewModel
    private var assignmentsList = arrayListOf<Assignment>()
    private var completeState = arrayListOf<Assignment>()
    private var completedAssignmentsList = arrayListOf<Assignment>()
    private var adapter: AssignmentsAdapter? = null
    private var completedAdapter: AssignmentsAdapter? = null
    val db: OnPointAppDatabase = getDbInstance(null)
    private val moodleDao: MoodleDao = db.getMoodleDao()
    private val assignmentDao: AssignmentDao = db.getAssignmentDao()
    // TODO latestAssignmentId replaced with one obtained from the DB (auto-incrementing integer). See how the TodoList does it.

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        pageViewModel = ViewModelProvider(this).get(PageViewModel::class.java).apply {
            setIndex(arguments?.getInt(ARG_SECTION_NUMBER) ?: 1)
        }
        // Fill the assignment list retrieved from the persistent database
        assignmentsList.addAll(assignmentDao.selectAllActive())
        completeState.addAll(assignmentDao.selectAllActive())
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        val root = inflater.inflate(R.layout.fragment_assignments, container, false)
        root.findViewById<Button>(R.id.assignment_sync_assignments)
            .setOnClickListener { syncAssignments() }
        root.findViewById<FloatingActionButton>(R.id.custom_assignment_add_button)
            .setOnClickListener { addCustomAssignment() }
        // TODO replace with assignments obtained from the Moodle API on startup
        // List of dummy assignments, inserted only the first time
        if (assignmentsList.isEmpty()) {
            for (i in 0..50) {
                addAssignmentCustomToAssignmentList(
                    "Dummy Assignment $i",
                    "Dummy Description $i",
                    Date(Date().time + (24L * 3600 * 1000 * i) + 60000L),
                    arrayListOf(URL("https://www.tugraz.at"), URL("https://tc.tugraz.at")),
                )
            }
        }
        if (completedAssignmentsList.isEmpty()) {
            for (i in 51..52) {
                addAssignmentCustomToAssignmentList(
                    "Dummy Assignment $i",
                    "Dummy Description $i",
                    Date(Date().time + (24L * 3600 * 1000 * i) + 60000L),
                    arrayListOf(URL("https://www.tugraz.at"), URL("https://tc.tugraz.at")),
                    1
                )
            }
        }

        // Create the the Recyclerview, make it a linear list (not a grid), assign the list of
        // items to it and provide and adapter constructing each element of the list as a TextView
        val assignmentsRecView: RecyclerView = root.findViewById(R.id.assignmentsList)
        assignmentsRecView.layoutManager = LinearLayoutManager(this.context)
        adapter =
            this.context?.let { AssignmentsAdapter(assignmentsList, it, parentFragmentManager, this) }
        assignmentsRecView.adapter = this.adapter

        // Create the Recyclerview for completed assignments, make it a linear list (not a grid), assign the list of
        // items to it and provide and adapter constructing each element of the list as a TextView
        val completedAssignmentsRecView: RecyclerView = root.findViewById(R.id.assignmentListDone)
        completedAssignmentsRecView.layoutManager = LinearLayoutManager(this.context)
        completedAdapter =
            this.context?.let { AssignmentsAdapter(completedAssignmentsList, it, parentFragmentManager, this) }
        completedAssignmentsRecView.adapter = this.completedAdapter

        val searchView: SearchView = root.findViewById(R.id.assignment_searchview)
        searchView.setOnQueryTextListener(object : SearchView.OnQueryTextListener {
            override fun onQueryTextChange(newText: String?): Boolean {
                if (newText != null){
                    val temp = completeState.toMutableList()
                    assignmentsList.clear()
                    assignmentsList.addAll(filter(temp, newText) as ArrayList<Assignment>)
                    adapter!!.notifyDataSetChanged()
                    return true
                }
                return false
            }

            override fun onQueryTextSubmit(query: String?): Boolean {
                if (query != null) {
                    val temp = completeState.toMutableList()
                    assignmentsList.clear()
                    assignmentsList.addAll(filter(temp, query) as ArrayList<Assignment>)
                    adapter!!.notifyDataSetChanged()
                    return true
                }
                return false
            }
        })

        return root
    }

    private fun addCustomAssignment() {
        val fragment = CustomAssignmentDialog(this)
        fragment.show(parentFragmentManager, null)
    }

    private fun syncAssignments() {
        val moodleApi = API()

        for (account in moodleDao.selectAll()) {
            moodleApi.setAuthority(account.apiLink)
            moodleApi.login(account.userName, account.password) { response: Any ->
                run {
                    if (response is LoginSuccessData) {
                        moodleApi.getAssignments { response: Any ->
                            run {
                                if (response is AssignmentError) {
                                    println(response.message)
                                }
                                if (response is AssignmentResponse) {
                                    addAssignmentsFromMoodle(
                                        response.courses,
                                        moodleApi.getAuthority()
                                    )
                                }
                            }
                        }
                    }
                    if (response is LoginErrorData) {
                        println(response.error)
                    }
                }
            }
        }

    }

    private fun addAssignmentsFromMoodle(courses: List<Course>, apiLink: String) {
        for (course in courses) {
            for (moodle_ass in course.assignments) {
                val link: String =
                    "https://" + apiLink + "/mod/assign/view.php?id=" + moodle_ass.cmid.toString()
                val assignment = Assignment(
                    moodle_ass.name,
                    moodle_ass.intro,
                    moodle_ass.duedate,
                    link,
                    moodle_ass.id
                )
                addAssignmentCustomToAssignmentList(
                    assignment.title,
                    assignment.description,
                    assignment.getDeadlineDate(),
                    assignment.getLinksAsUrls()
                )
            }
        }
    }


    /**
     * Appends an assignment as received from Moodle, refreshing the recycler view and the
     * notifications for the deadlines.
     */
    private fun addAssignmentFromMoodleToAssignmentList(
        title: String, description: String, deadline: Date, links: List<URL>? = null, moodleId: Int, done: Int? = 0
    ) {
        val uid: Long =
            assignmentDao.insertOneFromMoodle(title, description, deadline, links, moodleId, done)
        val assignment = assignmentDao.selectOne(uid)
        assignmentsList.add(assignment)
        completeState.add(assignment)
        adapter?.notifyDataSetChanged()
    }

    /**
     * Appends an assignment written by the user, refreshing the recycler view and the notifications
     * for the deadlines.
     */
     fun addAssignmentCustomToAssignmentList(
        title: String, description: String, deadline: Date, links: List<URL>? = null, done: Int? = 0
    ) {
        val uid: Long = assignmentDao.insertOneCustom(title, description, deadline, links, done)
        val assignment = assignmentDao.selectOne(uid)
        if(done == 0){
            assignmentsList.add(assignment)
            completeState.add(assignment)
            adapter?.notifyDataSetChanged()
        }else{
            completedAssignmentsList.add(assignment)
            completedAdapter?.notifyDataSetChanged()
        }

    }


    fun markAssignmentAsDone(assignment: Assignment) {
        completedAssignmentsList.add(assignment);
        assignmentsList.remove(assignment);
        completeState.remove(assignment);
        adapter?.notifyDataSetChanged();
        completedAdapter?.notifyDataSetChanged();
    }

    companion object {
        /**
         * The fragment argument representing the section number for this
         * fragment.
         */
        private const val ARG_SECTION_NUMBER = "section_number"


        fun filter(assignments: List<Assignment>, search: String): List<Assignment> {
            val lowerCaseQuery = search.toLowerCase(Locale.ROOT)
            val found: MutableList<Assignment> = ArrayList()
            for (assi in assignments) {
                val text = assi.title.toLowerCase(Locale.ROOT)
                if (text.contains(lowerCaseQuery)) {
                    found.add(assi)
                }
            }
            return found
        }

        /**
         * Returns a new instance of this fragment for the given section
         * number.
         */
        @JvmStatic
        fun newInstance(sectionNumber: Int): AssignmentsTabFragment {
            return AssignmentsTabFragment().apply {
                arguments = Bundle().apply {
                    putInt(ARG_SECTION_NUMBER, sectionNumber)
                }
            }
        }
    }
}

@Entity
data class Assignment(
    @ColumnInfo(name = "title")
    val title: String,

    @ColumnInfo(name = "description")
    val description: String,

    @ColumnInfo(name = "deadline")
    var deadlineUnixTime: Long,

    @ColumnInfo(name = "links")
    var links: String = "",

    @PrimaryKey(autoGenerate = true)
    var uid: Int? = null,

    @ColumnInfo(name = "moodle_id")
    var moodleId: Int? = null,

    @ColumnInfo(name = "done")
    var done: Int? = 0
) {

    companion object {
        fun convertDeadlineDate(deadline: Date): Long {
            return deadline.time / 1000
        }

        fun encodeLinks(linksList: List<URL>): String {
            var encoded = ""
            linksList.forEach {
                encoded += "$it;"
            }
            return encoded.trimEnd(';')
        }
    }

    fun getDeadlineDate(): Date {
        return Date(deadlineUnixTime * 1000)
    }

    fun getLinksAsUrls(): List<URL> {
        if(links.isEmpty()) {
            return emptyList()
        }
        return links.split(";").map {
            URL(it)
        }
    }

    fun linksToMultiLineString(): String {
        val text: StringBuilder = StringBuilder()
        getLinksAsUrls().forEach {
            text.append(it)
            text.append('\n')
        }
        return text.toString()
    }

    // Call this function ONLY after the ID is set.
    fun buildAndScheduleNotification(context: Context, reminder_date: Calendar) {
        val intentToLaunchNotification = Intent(context, ScheduledNotificationReceiver::class.java)
        intentToLaunchNotification.putExtra(
            "title",
            context.getString(R.string.assignment_notification_title)
        )
        intentToLaunchNotification.putExtra(
            "text",
            this.title + ": " + this.getDeadlineDate().toString()
        )
        intentToLaunchNotification.putExtra("notificationId", uid)
        // Schedule notification
        val pending = PendingIntent.getBroadcast(
            context,
            uid!!,
            intentToLaunchNotification,
            PendingIntent.FLAG_UPDATE_CURRENT
        )
        val manager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        manager.set(AlarmManager.RTC_WAKEUP, reminder_date.timeInMillis, pending)
    }
}

private class AssignmentsAdapter(
    val items: ArrayList<Assignment>,
    val context: Context,
    val fragmentManager: FragmentManager,
    val fragment: AssignmentsTabFragment
) :
    RecyclerView.Adapter<ViewHolder>() {
    override fun getItemCount(): Int {
        return items.size
    }

    // Factory creating each element of the RecyclerView
    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        return ViewHolder(
            LayoutInflater.from(context).inflate(R.layout.assignment_list_entry, parent, false),
            fragmentManager,
            fragment
        )
    }

    // Filler of the content of each TextView in the RecyclerView
    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.assignmentListEntryTextView.text = items[position].title
        holder.assignment = items[position]
    }
}

private class ViewHolder(view: View, val fragmentManager: FragmentManager, val assignment_fragment: AssignmentsTabFragment) :
    RecyclerView.ViewHolder(view) {
    lateinit var assignment: Assignment
    val assignmentListEntryTextView: TextView = view.findViewById(R.id.assignmentsListEntry)

    init {
        assignmentListEntryTextView.setOnClickListener {
            displayAssignmentDetailsAsDialog()
        }
    }

    private fun displayAssignmentDetailsAsDialog() {
        // At this point this.assignment should be initialised, as is is set by onBindViewHolder().
        // Otherwise it would not make much sense: how can we click (tick, mark) a view
        // which was never displayed?
        val fragment = AssignmentDetailsFragment(assignment)
        fragment.show(fragmentManager, null)
        fragment.onDoneButtonClicked = {
            val isAssignmentDone: Boolean = fragment.isAssignmentDone;
            if(isAssignmentDone) {
                val assignment = fragment.assignment;
                assignment_fragment.markAssignmentAsDone(assignment);
            }
        }
    }
}
