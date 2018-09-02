package com.ustadmobile.test.port.android.view;

import android.arch.paging.DataSource;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.support.test.InstrumentationRegistry;
import android.support.test.espresso.intent.Intents;
import android.support.test.espresso.intent.rule.IntentsTestRule;
import android.support.test.rule.ActivityTestRule;
import android.support.test.runner.AndroidJUnit4;
import android.view.View;

import org.hamcrest.Matcher;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

import com.aurelhubert.ahbottomnavigation.AHBottomNavigation;
import com.toughra.ustadmobile.R;
import com.ustadmobile.core.controller.ClazzListPresenter;
import com.ustadmobile.core.controller.ClazzLogDetailPresenter;
import com.ustadmobile.core.db.UmAppDatabase;
import com.ustadmobile.core.db.dao.ClazzDao;
import com.ustadmobile.core.db.dao.ClazzMemberDao;
import com.ustadmobile.lib.db.entities.Clazz;
import com.ustadmobile.lib.db.entities.ClazzMember;
import com.ustadmobile.port.android.view.BasePointActivity2;
import com.ustadmobile.port.android.view.ClassDetailActivity;
import com.ustadmobile.port.android.view.ClassLogDetailActivity;

import static android.support.test.espresso.Espresso.onView;
import static android.support.test.espresso.action.ViewActions.click;
import static android.support.test.espresso.assertion.ViewAssertions.matches;
import static android.support.test.espresso.intent.Intents.intended;
import static android.support.test.espresso.intent.matcher.IntentMatchers.hasAction;
import static android.support.test.espresso.intent.matcher.IntentMatchers.hasComponent;
import static android.support.test.espresso.intent.matcher.IntentMatchers.hasExtra;
import static android.support.test.espresso.matcher.ViewMatchers.hasDescendant;
import static android.support.test.espresso.matcher.ViewMatchers.hasSibling;
import static android.support.test.espresso.matcher.ViewMatchers.isDisplayed;
import static android.support.test.espresso.matcher.ViewMatchers.withChild;
import static android.support.test.espresso.matcher.ViewMatchers.withText;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.core.AllOf.allOf;
import static android.support.test.espresso.matcher.ViewMatchers.withId;



/**
 * ClazzListFragment's Espresso UI Test for Android
 */
@RunWith(AndroidJUnit4.class)
public class ClassListFragmentEspressoTest {


    private static final String TEST_CLASS_NAME = "Class A";
    private static final float TEST_CLASS_PERCENTAGE = 0.42F;
    private static final float TEST_STUDENT_PERCENTAGE = 0.21F;
    private static final long TEST_USER_UID = 1L;

    /**
     * This sets the activity that we want floating around
     */
    @Rule
    public IntentsTestRule<BasePointActivity2> mActivityRule =
            new IntentsTestRule<>(BasePointActivity2.class, false, false);


    private Clazz testClazz;

    @Before
    public void beforeTest() throws Throwable{
        Context context = InstrumentationRegistry.getTargetContext();
        UmAppDatabase.getInstance(context).clearAllTables();

        ClazzDao clazzDao = UmAppDatabase.getInstance(context).getClazzDao();
        ClazzMemberDao clazzMemberDao = UmAppDatabase.getInstance(context).getClazzMemberDao();

        testClazz = new Clazz();
        testClazz.setClazzName(TEST_CLASS_NAME);
        testClazz.setAttendanceAverage(TEST_CLASS_PERCENTAGE);
        testClazz.setClazzUid(clazzDao.insert(testClazz));

        for (int i=0; i<2; i++){
            ClazzMember clazzMember = new ClazzMember();
            clazzMember.setAttendancePercentage(TEST_STUDENT_PERCENTAGE);
            clazzMember.setClazzMemberClazzUid(testClazz.getClazzUid());
            clazzMember.setRole(ClazzMember.ROLE_STUDENT);
            clazzMember.setClazzMemberPersonUid(TEST_USER_UID);

            clazzMemberDao.insert(clazzMember);
        }


        //Before here..
        mActivityRule.launchActivity(new Intent());

        mActivityRule.runOnUiThread(() ->
                ((AHBottomNavigation)mActivityRule.getActivity().findViewById(R.id.bottom_navigation)
                ).setCurrentItem(1));
    }

    @Test
    public void givenAppLoads_whenClassListFragmentStarts_shouldShowClassesCards() {
        Assert.assertTrue(true);

        String attendancePercentage = (TEST_STUDENT_PERCENTAGE * 100) + "%";

        onView(allOf(withId(R.id.item_clazzlist_clazz_title), withText(TEST_CLASS_NAME)))
                .check(matches(isDisplayed()));
        onView(allOf(hasSibling(withText(TEST_CLASS_NAME)),
                withId(R.id.item_clazzlist_attendance_percentage),
                withText(containsString(attendancePercentage)))).check(matches(isDisplayed()));

    }

    @Test
    public void givenClassesListCardLoads_whenItemRecordAttendanceButtonPressed_shouldLoadAttendanceActivity(){

        onView(allOf(withId(R.id.item_clazzlist_attendance_record_attendance_button),
                withChild(withText(TEST_CLASS_NAME)))).perform(click());

        intended(allOf(
                hasComponent(ClassLogDetailActivity.class.getCanonicalName()),
                hasExtra(equalTo(ClazzListPresenter.ARG_CLAZZ_UID),
                        equalTo(testClazz.getClazzUid()))
        ));


        //Assert that a class, the record attendance button opens up the attendance activity for that
        //class with students populated and today's date.
    }

    @Test
    public void givenClassListFragmentStarted_whenClassClicked_shouldLaunchClassDetailActivity(){
        onView(allOf(withId(R.id.item_clazzlist_clazz_card),
                hasDescendant(withText(TEST_CLASS_NAME)))).perform(click());

        intended(allOf(
                hasComponent(new ComponentName(InstrumentationRegistry.getTargetContext(),
                        ClassDetailActivity.class)),
                hasExtra(equalTo(ClazzListPresenter.ARG_CLAZZ_UID),
                        equalTo(testClazz.getClazzUid()))
        ));

        //Assert that when class clicked goes to its Class Detail activity page.
    }

}
