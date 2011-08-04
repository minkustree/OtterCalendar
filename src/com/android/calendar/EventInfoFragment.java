/*
 * Copyright (C) 2010 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.android.calendar;

import static android.provider.CalendarContract.EXTRA_EVENT_BEGIN_TIME;
import static android.provider.CalendarContract.EXTRA_EVENT_END_TIME;
import static com.android.calendar.CalendarController.EVENT_EDIT_ON_LAUNCH;

import com.android.calendar.CalendarController.EventInfo;
import com.android.calendar.CalendarController.EventType;
import com.android.calendar.CalendarEventModel.Attendee;
import com.android.calendar.CalendarEventModel.ReminderEntry;
import com.android.calendar.event.AttendeesView;
import com.android.calendar.event.EditEventActivity;
import com.android.calendar.event.EditEventHelper;
import com.android.calendarcommon.EventRecurrence;
import com.android.calendar.event.EventViewUtils;

import android.app.Activity;
import android.app.Dialog;
import android.app.DialogFragment;
import android.app.Service;
import android.content.ActivityNotFoundException;
import android.content.ContentProviderOperation;
import android.content.ContentResolver;
import android.content.ContentUris;
import android.content.ContentValues;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.res.Resources;
import android.database.Cursor;
import android.graphics.Rect;
import android.graphics.Typeface;
import android.net.Uri;
import android.os.Bundle;
import android.provider.CalendarContract;
import android.provider.CalendarContract.Attendees;
import android.provider.CalendarContract.Calendars;
import android.provider.CalendarContract.Events;
import android.provider.CalendarContract.Reminders;
import android.provider.ContactsContract;
import android.provider.ContactsContract.CommonDataKinds;
import android.provider.ContactsContract.Intents;
import android.provider.ContactsContract.QuickContact;
import android.text.Spannable;
import android.text.SpannableStringBuilder;
import android.text.TextUtils;
import android.text.format.DateFormat;
import android.text.format.DateUtils;
import android.text.format.Time;
import android.text.style.ForegroundColorSpan;
import android.text.style.StrikethroughSpan;
import android.text.style.StyleSpan;
import android.text.util.Linkify;
import android.text.util.Rfc822Token;
import android.util.Log;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.MotionEvent;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.View.OnTouchListener;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowManager;
import android.view.accessibility.AccessibilityEvent;
import android.view.accessibility.AccessibilityManager;
import android.widget.AdapterView;
import android.widget.Button;
import android.widget.ImageButton;
import android.widget.LinearLayout;
import android.widget.RadioButton;
import android.widget.RadioGroup;
import android.widget.ScrollView;
import android.widget.RadioGroup.OnCheckedChangeListener;
import android.widget.TextView;
import android.widget.Toast;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Formatter;
import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;
import java.util.TimeZone;


public class EventInfoFragment extends DialogFragment implements OnCheckedChangeListener,
        CalendarController.EventHandler, OnClickListener {
    public static final boolean DEBUG = false;

    public static final String TAG = "EventInfoFragment";

    protected static final String BUNDLE_KEY_EVENT_ID = "key_event_id";
    protected static final String BUNDLE_KEY_START_MILLIS = "key_start_millis";
    protected static final String BUNDLE_KEY_END_MILLIS = "key_end_millis";
    protected static final String BUNDLE_KEY_IS_DIALOG = "key_fragment_is_dialog";
    protected static final String BUNDLE_KEY_ATTENDEE_RESPONSE = "key_attendee_response";

    private static final String PERIOD_SPACE = ". ";

    /**
     * These are the corresponding indices into the array of strings
     * "R.array.change_response_labels" in the resource file.
     */
    static final int UPDATE_SINGLE = 0;
    static final int UPDATE_ALL = 1;

    // Query tokens for QueryHandler
    private static final int TOKEN_QUERY_EVENT = 1 << 0;
    private static final int TOKEN_QUERY_CALENDARS = 1 << 1;
    private static final int TOKEN_QUERY_ATTENDEES = 1 << 2;
    private static final int TOKEN_QUERY_DUPLICATE_CALENDARS = 1 << 3;
    private static final int TOKEN_QUERY_REMINDERS = 1 << 4;
    private static final int TOKEN_QUERY_ALL = TOKEN_QUERY_DUPLICATE_CALENDARS
            | TOKEN_QUERY_ATTENDEES | TOKEN_QUERY_CALENDARS | TOKEN_QUERY_EVENT
            | TOKEN_QUERY_REMINDERS;
    private int mCurrentQuery = 0;

    private static final String[] EVENT_PROJECTION = new String[] {
        Events._ID,                  // 0  do not remove; used in DeleteEventHelper
        Events.TITLE,                // 1  do not remove; used in DeleteEventHelper
        Events.RRULE,                // 2  do not remove; used in DeleteEventHelper
        Events.ALL_DAY,              // 3  do not remove; used in DeleteEventHelper
        Events.CALENDAR_ID,          // 4  do not remove; used in DeleteEventHelper
        Events.DTSTART,              // 5  do not remove; used in DeleteEventHelper
        Events._SYNC_ID,             // 6  do not remove; used in DeleteEventHelper
        Events.EVENT_TIMEZONE,       // 7  do not remove; used in DeleteEventHelper
        Events.DESCRIPTION,          // 8
        Events.EVENT_LOCATION,       // 9
        Calendars.CALENDAR_ACCESS_LEVEL,      // 10
        Calendars.CALENDAR_COLOR,             // 11
        Events.HAS_ATTENDEE_DATA,    // 12
        Events.ORGANIZER,            // 13
        Events.HAS_ALARM,            // 14
        Calendars.MAX_REMINDERS,     //15
        Calendars.ALLOWED_REMINDERS, // 16
        Events.ORIGINAL_SYNC_ID      // 17 do not remove; used in DeleteEventHelper
    };
    private static final int EVENT_INDEX_ID = 0;
    private static final int EVENT_INDEX_TITLE = 1;
    private static final int EVENT_INDEX_RRULE = 2;
    private static final int EVENT_INDEX_ALL_DAY = 3;
    private static final int EVENT_INDEX_CALENDAR_ID = 4;
    private static final int EVENT_INDEX_SYNC_ID = 6;
    private static final int EVENT_INDEX_EVENT_TIMEZONE = 7;
    private static final int EVENT_INDEX_DESCRIPTION = 8;
    private static final int EVENT_INDEX_EVENT_LOCATION = 9;
    private static final int EVENT_INDEX_ACCESS_LEVEL = 10;
    private static final int EVENT_INDEX_COLOR = 11;
    private static final int EVENT_INDEX_HAS_ATTENDEE_DATA = 12;
    private static final int EVENT_INDEX_ORGANIZER = 13;
    private static final int EVENT_INDEX_HAS_ALARM = 14;
    private static final int EVENT_INDEX_MAX_REMINDERS = 15;
    private static final int EVENT_INDEX_ALLOWED_REMINDERS = 16;


    private static final String[] ATTENDEES_PROJECTION = new String[] {
        Attendees._ID,                      // 0
        Attendees.ATTENDEE_NAME,            // 1
        Attendees.ATTENDEE_EMAIL,           // 2
        Attendees.ATTENDEE_RELATIONSHIP,    // 3
        Attendees.ATTENDEE_STATUS,          // 4
    };
    private static final int ATTENDEES_INDEX_ID = 0;
    private static final int ATTENDEES_INDEX_NAME = 1;
    private static final int ATTENDEES_INDEX_EMAIL = 2;
    private static final int ATTENDEES_INDEX_RELATIONSHIP = 3;
    private static final int ATTENDEES_INDEX_STATUS = 4;

    private static final String ATTENDEES_WHERE = Attendees.EVENT_ID + "=?";

    private static final String ATTENDEES_SORT_ORDER = Attendees.ATTENDEE_NAME + " ASC, "
            + Attendees.ATTENDEE_EMAIL + " ASC";

    private static final String[] REMINDERS_PROJECTION = new String[] {
        Reminders._ID,                      // 0
        Reminders.MINUTES,            // 1
        Reminders.METHOD           // 2
    };
    private static final int REMINDERS_INDEX_ID = 0;
    private static final int REMINDERS_MINUTES_ID = 1;
    private static final int REMINDERS_METHOD_ID = 2;

    private static final String REMINDERS_WHERE = Reminders.EVENT_ID + "=?";

    static final String[] CALENDARS_PROJECTION = new String[] {
        Calendars._ID,           // 0
        Calendars.CALENDAR_DISPLAY_NAME,  // 1
        Calendars.OWNER_ACCOUNT, // 2
        Calendars.CAN_ORGANIZER_RESPOND // 3
    };
    static final int CALENDARS_INDEX_DISPLAY_NAME = 1;
    static final int CALENDARS_INDEX_OWNER_ACCOUNT = 2;
    static final int CALENDARS_INDEX_OWNER_CAN_RESPOND = 3;

    static final String CALENDARS_WHERE = Calendars._ID + "=?";
    static final String CALENDARS_DUPLICATE_NAME_WHERE = Calendars.CALENDAR_DISPLAY_NAME + "=?";

    private View mView;

    private Uri mUri;
    private long mEventId;
    private Cursor mEventCursor;
    private Cursor mAttendeesCursor;
    private Cursor mCalendarsCursor;
    private Cursor mRemindersCursor;

    private static float mScale = 0; // Used for supporting different screen densities

    private long mStartMillis;
    private long mEndMillis;

    private boolean mHasAttendeeData;
    private boolean mIsOrganizer;
    private long mCalendarOwnerAttendeeId = EditEventHelper.ATTENDEE_ID_NONE;
    private boolean mOwnerCanRespond;
    private String mCalendarOwnerAccount;
    private boolean mCanModifyCalendar;
    private boolean mIsBusyFreeCalendar;
    private int mNumOfAttendees;

    private EditResponseHelper mEditResponseHelper;

    private int mOriginalAttendeeResponse;
    private int mAttendeeResponseFromIntent = CalendarController.ATTENDEE_NO_RESPONSE;
    private boolean mIsRepeating;
    private boolean mHasAlarm;
    private int mMaxReminders;
    private String mCalendarAllowedReminders;

    private TextView mTitle;
    private TextView mWhen;
    private TextView mWhere;
    private TextView mWhat;
    private TextView mAttendees;
    private AttendeesView mLongAttendees;
    private Menu mMenu = null;
    private View mHeadlines;
    private ScrollView mScrollView;

    private Pattern mWildcardPattern = Pattern.compile("^.*$");

    ArrayList<Attendee> mAcceptedAttendees = new ArrayList<Attendee>();
    ArrayList<Attendee> mDeclinedAttendees = new ArrayList<Attendee>();
    ArrayList<Attendee> mTentativeAttendees = new ArrayList<Attendee>();
    ArrayList<Attendee> mNoResponseAttendees = new ArrayList<Attendee>();
    private int mColor;


    private int mDefaultReminderMinutes;
    private ArrayList<LinearLayout> mReminderViews = new ArrayList<LinearLayout>(0);
    public ArrayList<ReminderEntry> mReminders;
    public ArrayList<ReminderEntry> mOriginalReminders;

    /**
     * Contents of the "minutes" spinner.  This has default values from the XML file, augmented
     * with any additional values that were already associated with the event.
     */
    private ArrayList<Integer> mReminderMinuteValues;
    private ArrayList<String> mReminderMinuteLabels;

    /**
     * Contents of the "methods" spinner.  The "values" list specifies the method constant
     * (e.g. {@link Reminders#METHOD_ALERT}) associated with the labels.  Any methods that
     * aren't allowed by the Calendar will be removed.
     */
    private ArrayList<Integer> mReminderMethodValues;
    private ArrayList<String> mReminderMethodLabels;



    private QueryHandler mHandler;

    private Runnable mTZUpdater = new Runnable() {
        @Override
        public void run() {
            updateEvent(mView);
        }
    };

    private static int DIALOG_WIDTH = 500;
    private static int DIALOG_HEIGHT = 600;
    private boolean mIsDialog = false;
    private boolean mIsPaused = true;
    private boolean mDismissOnResume = false;
    private int mX = -1;
    private int mY = -1;
    private Button mDescButton;  // Button to expand/collapse the description
    private String mMoreLabel;   // Labels for the button
    private String mLessLabel;
    private boolean mShowMaxDescription;  // Current status of button
    private int mDescLineNum;             // The default number of lines in the description
    private boolean mIsTabletConfig;
    private Activity mActivity;

    private class QueryHandler extends AsyncQueryService {
        public QueryHandler(Context context) {
            super(context);
        }

        @Override
        protected void onQueryComplete(int token, Object cookie, Cursor cursor) {
            // if the activity is finishing, then close the cursor and return
            final Activity activity = getActivity();
            if (activity == null || activity.isFinishing()) {
                cursor.close();
                return;
            }

            switch (token) {
            case TOKEN_QUERY_EVENT:
                mEventCursor = Utils.matrixCursorFromCursor(cursor);
                if (initEventCursor()) {
                    // The cursor is empty. This can happen if the event was
                    // deleted.
                    // FRAG_TODO we should no longer rely on Activity.finish()
                    activity.finish();
                    return;
                }
                updateEvent(mView);

                // start calendar query
                Uri uri = Calendars.CONTENT_URI;
                String[] args = new String[] {
                        Long.toString(mEventCursor.getLong(EVENT_INDEX_CALENDAR_ID))};
                startQuery(TOKEN_QUERY_CALENDARS, null, uri, CALENDARS_PROJECTION,
                        CALENDARS_WHERE, args, null);
                break;
            case TOKEN_QUERY_CALENDARS:
                mCalendarsCursor = Utils.matrixCursorFromCursor(cursor);
                updateCalendar(mView);
                // FRAG_TODO fragments shouldn't set the title anymore
                updateTitle();

                if (!mIsBusyFreeCalendar) {
                    args = new String[] { Long.toString(mEventId) };

                    // start attendees query
                    uri = Attendees.CONTENT_URI;
                    startQuery(TOKEN_QUERY_ATTENDEES, null, uri, ATTENDEES_PROJECTION,
                            ATTENDEES_WHERE, args, ATTENDEES_SORT_ORDER);
                } else {
                    sendAccessibilityEventIfQueryDone(TOKEN_QUERY_ATTENDEES);
                }
                mOriginalReminders = new ArrayList<ReminderEntry> ();
                if (mHasAlarm) {
                    // start reminders query
                    args = new String[] { Long.toString(mEventId) };
                    uri = Reminders.CONTENT_URI;
                    startQuery(TOKEN_QUERY_REMINDERS, null, uri,
                            REMINDERS_PROJECTION, REMINDERS_WHERE, args, null);
                } else {
                    sendAccessibilityEventIfQueryDone(TOKEN_QUERY_REMINDERS);
                }
                break;
            case TOKEN_QUERY_ATTENDEES:
                mAttendeesCursor = Utils.matrixCursorFromCursor(cursor);
                initAttendeesCursor(mView);
                updateResponse(mView);
                break;
            case TOKEN_QUERY_REMINDERS:
                mRemindersCursor = Utils.matrixCursorFromCursor(cursor);
                initReminders(mView, mRemindersCursor);
                break;
            case TOKEN_QUERY_DUPLICATE_CALENDARS:
                Resources res = activity.getResources();
                SpannableStringBuilder sb = new SpannableStringBuilder();

                // Label
                String label = res.getString(R.string.view_event_calendar_label);
                sb.append(label).append(" ");
                sb.setSpan(new StyleSpan(Typeface.BOLD), 0, label.length(),
                        Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);

                // Calendar display name
                String calendarName = mCalendarsCursor.getString(CALENDARS_INDEX_DISPLAY_NAME);
                sb.append(calendarName);

                // Show email account if display name is not unique and
                // display name != email
                String email = mCalendarsCursor.getString(CALENDARS_INDEX_OWNER_ACCOUNT);
                if (cursor.getCount() > 1 && !calendarName.equalsIgnoreCase(email)) {
                    sb.append(" (").append(email).append(")");
                }

                break;
            }
            cursor.close();
            sendAccessibilityEventIfQueryDone(token);
        }

    }

    private void sendAccessibilityEventIfQueryDone(int token) {
        mCurrentQuery |= token;
        if (mCurrentQuery == TOKEN_QUERY_ALL) {
            sendAccessibilityEvent();
        }
    }

    public EventInfoFragment(Context context, Uri uri, long startMillis, long endMillis,
            int attendeeResponse, boolean isDialog) {

        if (mScale == 0) {
            mScale = context.getResources().getDisplayMetrics().density;
            if (mScale != 1) {
                DIALOG_WIDTH *= mScale;
                DIALOG_HEIGHT *= mScale;
            }
        }
        mIsDialog = isDialog;

        setStyle(DialogFragment.STYLE_NO_TITLE, 0);
        mUri = uri;
        mStartMillis = startMillis;
        mEndMillis = endMillis;
        mAttendeeResponseFromIntent = attendeeResponse;
    }

    // This is currently required by the fragment manager.
    public EventInfoFragment() {
    }



    public EventInfoFragment(Context context, long eventId, long startMillis, long endMillis,
            int attendeeResponse, boolean isDialog) {
        this(context, ContentUris.withAppendedId(Events.CONTENT_URI, eventId), startMillis,
                endMillis, attendeeResponse, isDialog);
        mEventId = eventId;
    }

    @Override
    public void onActivityCreated(Bundle savedInstanceState) {
        super.onActivityCreated(savedInstanceState);

        if (savedInstanceState != null) {
            mIsDialog = savedInstanceState.getBoolean(BUNDLE_KEY_IS_DIALOG, false);
        }

        if (mIsDialog) {
            applyDialogParams();
        }
    }

    private void applyDialogParams() {
        Dialog dialog = getDialog();
        dialog.setCanceledOnTouchOutside(true);

        Window window = dialog.getWindow();
        window.addFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND);

        WindowManager.LayoutParams a = window.getAttributes();
        a.dimAmount = .4f;

        a.width = DIALOG_WIDTH;
        a.height = DIALOG_HEIGHT;


        // On tablets , do smart positioning of dialog
        // On phones , use the whole screen

        if (mX != -1 || mY != -1) {
            a.x = mX - a.width - 64;
            if (a.x < 0) {
                a.x = mX + 64;
            }
            a.y = mY - 64;
            a.gravity = Gravity.LEFT | Gravity.TOP;
        }
        window.setAttributes(a);
    }

    public void setDialogParams(int x, int y) {
        mX = x;
        mY = y;
    }

    // Implements OnCheckedChangeListener
    @Override
    public void onCheckedChanged(RadioGroup group, int checkedId) {
        // If this is not a repeating event, then don't display the dialog
        // asking which events to change.
        if (!mIsRepeating) {
            return;
        }

        // If the selection is the same as the original, then don't display the
        // dialog asking which events to change.
        if (checkedId == findButtonIdForResponse(mOriginalAttendeeResponse)) {
            return;
        }

        // This is a repeating event. We need to ask the user if they mean to
        // change just this one instance or all instances.
        mEditResponseHelper.showDialog(mEditResponseHelper.getWhichEvents());
    }

    public void onNothingSelected(AdapterView<?> parent) {
    }

    @Override
    public void onAttach(Activity activity) {
        super.onAttach(activity);
        mActivity = activity;
        mEditResponseHelper = new EditResponseHelper(activity);
        mHandler = new QueryHandler(activity);
        mDescLineNum = activity.getResources().getInteger((R.integer.event_info_desc_line_num));
        mMoreLabel = activity.getResources().getString((R.string.event_info_desc_more));
        mLessLabel = activity.getResources().getString((R.string.event_info_desc_less));
        if (!mIsDialog) {
            setHasOptionsMenu(true);
        }
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
            Bundle savedInstanceState) {
        mView = inflater.inflate(R.layout.event_info, container, false);
        mScrollView = (ScrollView) mView.findViewById(R.id.event_info_scroll_view);
        mTitle = (TextView) mView.findViewById(R.id.title);
        mWhen = (TextView) mView.findViewById(R.id.when);
        mWhere = (TextView) mView.findViewById(R.id.where);
        mWhat = (TextView) mView.findViewById(R.id.description);
        mAttendees = (TextView) mView.findViewById(R.id.attendee_list);
        mHeadlines = mView.findViewById(R.id.event_info_headline);
        mLongAttendees = (AttendeesView)mView.findViewById(R.id.long_attendee_list);
        mDescButton = (Button)mView.findViewById(R.id.desc_expand);
        mDescButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                mShowMaxDescription = !mShowMaxDescription;
                updateDescription();
            }
        });
        mShowMaxDescription = false; // Show short version of description as default.
        mIsTabletConfig = Utils.getConfigBool(mActivity, R.bool.tablet_config);

        if (mUri == null) {
            // restore event ID from bundle
            mEventId = savedInstanceState.getLong(BUNDLE_KEY_EVENT_ID);
            mUri = ContentUris.withAppendedId(Events.CONTENT_URI, mEventId);
            mStartMillis = savedInstanceState.getLong(BUNDLE_KEY_START_MILLIS);
            mEndMillis = savedInstanceState.getLong(BUNDLE_KEY_END_MILLIS);
        }

        // start loading the data
        mHandler.startQuery(TOKEN_QUERY_EVENT, null, mUri, EVENT_PROJECTION,
                null, null, null);

        Button b = (Button) mView.findViewById(R.id.delete);
        b.setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View v) {
                if (!mCanModifyCalendar) {
                    return;
                }
                DeleteEventHelper deleteHelper = new DeleteEventHelper(
                        getActivity(), getActivity(),
                        !mIsDialog && !mIsTabletConfig /* exitWhenDone */);
                deleteHelper.delete(mStartMillis, mEndMillis, mEventId, -1, onDeleteRunnable);
            }});

        // Hide Edit/Delete buttons if in full screen mode on a phone
        if (savedInstanceState != null) {
            mIsDialog = savedInstanceState.getBoolean(BUNDLE_KEY_IS_DIALOG, false);
        }
        if (!mIsDialog && !mIsTabletConfig) {
            mView.findViewById(R.id.event_info_buttons_container).setVisibility(View.GONE);
        }

        // Create a listener for the add reminder button

        ImageButton reminderAddButton = (ImageButton) mView.findViewById(R.id.reminder_add);
        View.OnClickListener addReminderOnClickListener = new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                addReminder();
            }
        };
        reminderAddButton.setOnClickListener(addReminderOnClickListener);

        // Set reminders variables

        SharedPreferences prefs = GeneralPreferences.getSharedPreferences(mActivity);
        String defaultReminderString = prefs.getString(
                GeneralPreferences.KEY_DEFAULT_REMINDER, GeneralPreferences.NO_REMINDER_STRING);
        mDefaultReminderMinutes = Integer.parseInt(defaultReminderString);
        prepareReminders();

        return mView;
    }

    private Runnable onDeleteRunnable = new Runnable() {
        @Override
        public void run() {
            if (EventInfoFragment.this.mIsPaused) {
                mDismissOnResume = true;
                return;
            }
            if (EventInfoFragment.this.isVisible()) {
                EventInfoFragment.this.dismiss();
            }
        }
    };

    // Sets the description:
    // Set the expand/collapse button
    // Expand/collapse the description according the the current status
    private void updateDescription() {
        // Description is short, hide button
        if (mWhat.getLineCount() <= mDescLineNum) {
            mDescButton.setVisibility(View.GONE);
            return;
        }
        // Show button and set label according to the expand/collapse status
        mDescButton.setVisibility(View.VISIBLE);
        if (mShowMaxDescription) {
            mDescButton.setText(mLessLabel);
            mWhat.setLines(mWhat.getLineCount());
        } else {
            mDescButton.setText(mMoreLabel);
            mWhat.setLines(mDescLineNum);
        }
    }

    private void updateTitle() {
        Resources res = getActivity().getResources();
        if (mCanModifyCalendar && !mIsOrganizer) {
            getActivity().setTitle(res.getString(R.string.event_info_title_invite));
        } else {
            getActivity().setTitle(res.getString(R.string.event_info_title));
        }
    }

    /**
     * Initializes the event cursor, which is expected to point to the first
     * (and only) result from a query.
     * @return true if the cursor is empty.
     */
    private boolean initEventCursor() {
        if ((mEventCursor == null) || (mEventCursor.getCount() == 0)) {
            return true;
        }
        mEventCursor.moveToFirst();
        mEventId = mEventCursor.getInt(EVENT_INDEX_ID);
        String rRule = mEventCursor.getString(EVENT_INDEX_RRULE);
        mIsRepeating = !TextUtils.isEmpty(rRule);
        mHasAlarm = (mEventCursor.getInt(EVENT_INDEX_HAS_ALARM) == 1)?true:false;
        mMaxReminders = mEventCursor.getInt(EVENT_INDEX_MAX_REMINDERS);
        mCalendarAllowedReminders =  mEventCursor.getString(EVENT_INDEX_ALLOWED_REMINDERS);
        return false;
    }

    @SuppressWarnings("fallthrough")
    private void initAttendeesCursor(View view) {
        mOriginalAttendeeResponse = CalendarController.ATTENDEE_NO_RESPONSE;
        mCalendarOwnerAttendeeId = EditEventHelper.ATTENDEE_ID_NONE;
        mNumOfAttendees = 0;
        if (mAttendeesCursor != null) {
            mNumOfAttendees = mAttendeesCursor.getCount();
            if (mAttendeesCursor.moveToFirst()) {
                mAcceptedAttendees.clear();
                mDeclinedAttendees.clear();
                mTentativeAttendees.clear();
                mNoResponseAttendees.clear();

                do {
                    int status = mAttendeesCursor.getInt(ATTENDEES_INDEX_STATUS);
                    String name = mAttendeesCursor.getString(ATTENDEES_INDEX_NAME);
                    String email = mAttendeesCursor.getString(ATTENDEES_INDEX_EMAIL);

                    if (mCalendarOwnerAttendeeId == EditEventHelper.ATTENDEE_ID_NONE &&
                            mCalendarOwnerAccount.equalsIgnoreCase(email)) {
                        mCalendarOwnerAttendeeId = mAttendeesCursor.getInt(ATTENDEES_INDEX_ID);
                        mOriginalAttendeeResponse = mAttendeesCursor.getInt(ATTENDEES_INDEX_STATUS);
                    } else {
                        // Don't show your own status in the list because:
                        //  1) it doesn't make sense for event without other guests.
                        //  2) there's a spinner for that for events with guests.
                        switch(status) {
                            case Attendees.ATTENDEE_STATUS_ACCEPTED:
                                mAcceptedAttendees.add(new Attendee(name, email,
                                        Attendees.ATTENDEE_STATUS_ACCEPTED));
                                break;
                            case Attendees.ATTENDEE_STATUS_DECLINED:
                                mDeclinedAttendees.add(new Attendee(name, email,
                                        Attendees.ATTENDEE_STATUS_DECLINED));
                                break;
                            case Attendees.ATTENDEE_STATUS_TENTATIVE:
                                mTentativeAttendees.add(new Attendee(name, email,
                                        Attendees.ATTENDEE_STATUS_TENTATIVE));
                                break;
                            default:
                                mNoResponseAttendees.add(new Attendee(name, email,
                                        Attendees.ATTENDEE_STATUS_NONE));
                        }
                    }
                } while (mAttendeesCursor.moveToNext());
                mAttendeesCursor.moveToFirst();

                updateAttendees(view);
            }
        }
    }

    @Override
    public void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);
        outState.putLong(BUNDLE_KEY_EVENT_ID, mEventId);
        outState.putLong(BUNDLE_KEY_START_MILLIS, mStartMillis);
        outState.putLong(BUNDLE_KEY_END_MILLIS, mEndMillis);
        outState.putBoolean(BUNDLE_KEY_IS_DIALOG, mIsDialog);
        outState.putInt(BUNDLE_KEY_ATTENDEE_RESPONSE, mAttendeeResponseFromIntent);
    }


    @Override
    public void onCreateOptionsMenu(Menu menu, MenuInflater inflater) {
        super.onCreateOptionsMenu(menu, inflater);
        // Show edit/delete buttons only in non-dialog configuration on a phone
        if (!mIsDialog && !mIsTabletConfig) {
            inflater.inflate(R.menu.event_info_title_bar, menu);
            mMenu = menu;
            updateMenu();
        }
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {

        // If we're a dialog or part of a tablet display we don't want to handle
        // menu buttons
        if (mIsDialog || mIsTabletConfig) {
            return false;
        }
        // Handles option menu selections:
        // Home button - close event info activity and start the main calendar
        // one
        // Edit button - start the event edit activity and close the info
        // activity
        // Delete button - start a delete query that calls a runnable that close
        // the info activity

        switch (item.getItemId()) {
            case android.R.id.home:
                Intent launchIntent = new Intent();
                launchIntent.setAction(Intent.ACTION_VIEW);
                launchIntent.setData(Uri.parse(CalendarContract.CONTENT_URI + "/time"));
                launchIntent.setFlags(Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED
                        | Intent.FLAG_ACTIVITY_CLEAR_TOP);
                launchIntent.setClass(mActivity, AllInOneActivity.class);
                startActivity(launchIntent);
                mActivity.finish();
                return true;
            case R.id.info_action_edit:
                Uri uri = ContentUris.withAppendedId(Events.CONTENT_URI, mEventId);
                Intent intent = new Intent(Intent.ACTION_EDIT, uri);
                intent.putExtra(EXTRA_EVENT_BEGIN_TIME, mStartMillis);
                intent.putExtra(EXTRA_EVENT_END_TIME, mEndMillis);
                intent.setClass(mActivity, EditEventActivity.class);
                intent.putExtra(EVENT_EDIT_ON_LAUNCH, true);
                startActivity(intent);
                mActivity.finish();
                break;
            case R.id.info_action_delete:
                DeleteEventHelper deleteHelper =
                        new DeleteEventHelper(mActivity, mActivity, true /* exitWhenDone */);
                deleteHelper.delete(mStartMillis, mEndMillis, mEventId, -1, onDeleteRunnable);
                break;
            default:
                break;
        }
        return super.onOptionsItemSelected(item);
    }

    @Override
    public void onDestroyView() {
        if (saveResponse() || saveReminders()) {
            Toast.makeText(getActivity(), R.string.saving_event, Toast.LENGTH_SHORT).show();
        }
        super.onDestroyView();
    }

    @Override
    public void onDestroy() {
        if (mEventCursor != null) {
            mEventCursor.close();
        }
        if (mCalendarsCursor != null) {
            mCalendarsCursor.close();
        }
        if (mAttendeesCursor != null) {
            mAttendeesCursor.close();
        }
        super.onDestroy();
    }

    /**
     * Asynchronously saves the response to an invitation if the user changed
     * the response. Returns true if the database will be updated.
     *
     * @return true if the database will be changed
     */
    private boolean saveResponse() {
        if (mAttendeesCursor == null || mEventCursor == null) {
            return false;
        }

        RadioGroup radioGroup = (RadioGroup) getView().findViewById(R.id.response_value);
        int status = getResponseFromButtonId(radioGroup.getCheckedRadioButtonId());
        if (status == Attendees.ATTENDEE_STATUS_NONE) {
            return false;
        }

        // If the status has not changed, then don't update the database
        if (status == mOriginalAttendeeResponse) {
            return false;
        }

        // If we never got an owner attendee id we can't set the status
        if (mCalendarOwnerAttendeeId == EditEventHelper.ATTENDEE_ID_NONE) {
            return false;
        }

        if (!mIsRepeating) {
            // This is a non-repeating event
            updateResponse(mEventId, mCalendarOwnerAttendeeId, status);
            return true;
        }

        // This is a repeating event
        int whichEvents = mEditResponseHelper.getWhichEvents();
        switch (whichEvents) {
            case -1:
                return false;
            case UPDATE_SINGLE:
                createExceptionResponse(mEventId, status);
                return true;
            case UPDATE_ALL:
                updateResponse(mEventId, mCalendarOwnerAttendeeId, status);
                return true;
            default:
                Log.e(TAG, "Unexpected choice for updating invitation response");
                break;
        }
        return false;
    }

    private void updateResponse(long eventId, long attendeeId, int status) {
        // Update the attendee status in the attendees table.  the provider
        // takes care of updating the self attendance status.
        ContentValues values = new ContentValues();

        if (!TextUtils.isEmpty(mCalendarOwnerAccount)) {
            values.put(Attendees.ATTENDEE_EMAIL, mCalendarOwnerAccount);
        }
        values.put(Attendees.ATTENDEE_STATUS, status);
        values.put(Attendees.EVENT_ID, eventId);

        Uri uri = ContentUris.withAppendedId(Attendees.CONTENT_URI, attendeeId);

        mHandler.startUpdate(mHandler.getNextToken(), null, uri, values,
                null, null, Utils.UNDO_DELAY);
    }

    /**
     * Creates an exception to a recurring event.  The only change we're making is to the
     * "self attendee status" value.  The provider will take care of updating the corresponding
     * Attendees.attendeeStatus entry.
     *
     * @param eventId The recurring event.
     * @param status The new value for selfAttendeeStatus.
     */
    private void createExceptionResponse(long eventId, int status) {
        ContentValues values = new ContentValues();
        values.put(Events.ORIGINAL_INSTANCE_TIME, mStartMillis);
        values.put(Events.SELF_ATTENDEE_STATUS, status);
        values.put(Events.STATUS, Events.STATUS_CONFIRMED);

        ArrayList<ContentProviderOperation> ops = new ArrayList<ContentProviderOperation>();
        Uri exceptionUri = Uri.withAppendedPath(Events.CONTENT_EXCEPTION_URI,
                String.valueOf(eventId));
        ops.add(ContentProviderOperation.newInsert(exceptionUri).withValues(values).build());

        mHandler.startBatch(mHandler.getNextToken(), null, CalendarContract.AUTHORITY, ops,
                Utils.UNDO_DELAY);
   }

    public static int getResponseFromButtonId(int buttonId) {
        int response;
        switch (buttonId) {
            case R.id.response_yes:
                response = Attendees.ATTENDEE_STATUS_ACCEPTED;
                break;
            case R.id.response_maybe:
                response = Attendees.ATTENDEE_STATUS_TENTATIVE;
                break;
            case R.id.response_no:
                response = Attendees.ATTENDEE_STATUS_DECLINED;
                break;
            default:
                response = Attendees.ATTENDEE_STATUS_NONE;
        }
        return response;
    }

    public static int findButtonIdForResponse(int response) {
        int buttonId;
        switch (response) {
            case Attendees.ATTENDEE_STATUS_ACCEPTED:
                buttonId = R.id.response_yes;
                break;
            case Attendees.ATTENDEE_STATUS_TENTATIVE:
                buttonId = R.id.response_maybe;
                break;
            case Attendees.ATTENDEE_STATUS_DECLINED:
                buttonId = R.id.response_no;
                break;
                default:
                    buttonId = -1;
        }
        return buttonId;
    }

    private void doEdit() {
        Context c = getActivity();
        // This ensures that we aren't in the process of closing and have been
        // unattached already
        if (c != null) {
            CalendarController.getInstance(c).sendEventRelatedEvent(
                    this, EventType.VIEW_EVENT_DETAILS, mEventId, mStartMillis, mEndMillis, 0
                    , 0, -1);
        }
    }

    private void updateEvent(View view) {
        if (mEventCursor == null || view == null) {
            return;
        }

        String eventName = mEventCursor.getString(EVENT_INDEX_TITLE);
        if (eventName == null || eventName.length() == 0) {
            eventName = getActivity().getString(R.string.no_title_label);
        }

        boolean allDay = mEventCursor.getInt(EVENT_INDEX_ALL_DAY) != 0;
        String location = mEventCursor.getString(EVENT_INDEX_EVENT_LOCATION);
        String description = mEventCursor.getString(EVENT_INDEX_DESCRIPTION);
        String rRule = mEventCursor.getString(EVENT_INDEX_RRULE);
        String eventTimezone = mEventCursor.getString(EVENT_INDEX_EVENT_TIMEZONE);
        String organizer = mEventCursor.getString(EVENT_INDEX_ORGANIZER);

        mColor = Utils.getDisplayColorFromColor(mEventCursor.getInt(EVENT_INDEX_COLOR));
        mHeadlines.setBackgroundColor(mColor);

        // What
        if (eventName != null) {
            setTextCommon(view, R.id.title, eventName);
        }

        // When
        // Set the date and repeats (if any)
        String whenDate;
        int flagsTime = DateUtils.FORMAT_SHOW_TIME;
        int flagsDate = DateUtils.FORMAT_SHOW_DATE | DateUtils.FORMAT_SHOW_WEEKDAY |
                DateUtils.FORMAT_SHOW_YEAR;

        if (DateFormat.is24HourFormat(getActivity())) {
            flagsTime |= DateUtils.FORMAT_24HOUR;
        }

        // Put repeat after the date (if any)
        String repeatString = null;
        if (!TextUtils.isEmpty(rRule)) {
            EventRecurrence eventRecurrence = new EventRecurrence();
            eventRecurrence.parse(rRule);
            Time date = new Time(Utils.getTimeZone(getActivity(), mTZUpdater));
            if (allDay) {
                date.timezone = Time.TIMEZONE_UTC;
            }
            date.set(mStartMillis);
            eventRecurrence.setStartDate(date);
            repeatString = EventRecurrenceFormatter.getRepeatString(
                    getActivity().getResources(), eventRecurrence);
        }
        // If an all day event , show the date without the time
        if (allDay) {
            Formatter f = new Formatter(new StringBuilder(50), Locale.getDefault());
            whenDate = DateUtils.formatDateRange(getActivity(), f, mStartMillis, mStartMillis,
                    flagsDate, Time.TIMEZONE_UTC).toString();
            if (repeatString != null) {
                setTextCommon(view, R.id.when_date, whenDate + " (" + repeatString + ")");
            } else {
                setTextCommon(view, R.id.when_date, whenDate);
            }
            view.findViewById(R.id.when_time).setVisibility(View.GONE);

        } else {
            // Show date for none all-day events
            whenDate = Utils.formatDateRange(getActivity(), mStartMillis, mEndMillis, flagsDate);
            String whenTime = Utils.formatDateRange(getActivity(), mStartMillis, mEndMillis,
                    flagsTime);
            if (repeatString != null) {
                setTextCommon(view, R.id.when_date, whenDate + " (" + repeatString + ")");
            } else {
                setTextCommon(view, R.id.when_date, whenDate);
            }

            // Show the event timezone if it is different from the local timezone after the time
            String localTimezone = Utils.getTimeZone(mActivity, mTZUpdater);
            if (!TextUtils.equals(localTimezone, eventTimezone)) {
                String displayName;
                // Figure out if this is in DST
                Time date = new Time(Utils.getTimeZone(getActivity(), mTZUpdater));
                if (allDay) {
                    date.timezone = Time.TIMEZONE_UTC;
                }
                date.set(mStartMillis);

                TimeZone tz = TimeZone.getTimeZone(localTimezone);
                if (tz == null || tz.getID().equals("GMT")) {
                    displayName = localTimezone;
                } else {
                    displayName = tz.getDisplayName(date.isDst != 0, TimeZone.LONG);
                }
                setTextCommon(view, R.id.when_time, whenTime + " (" + displayName + ")");
            }
            else {
                setTextCommon(view, R.id.when_time, whenTime);
            }
        }


        // Organizer view is setup in the updateCalendar method


        // Where
        if (location == null || location.trim().length() == 0) {
            setVisibilityCommon(view, R.id.where, View.GONE);
        } else {
            final TextView textView = mWhere;
            if (textView != null) {
                textView.setAutoLinkMask(0);
                textView.setText(location.trim());
                if (!Linkify.addLinks(textView, Linkify.WEB_URLS | Linkify.EMAIL_ADDRESSES
                        | Linkify.MAP_ADDRESSES)) {
                    Linkify.addLinks(textView, mWildcardPattern, "geo:0,0?q=");
                }
                textView.setOnTouchListener(new OnTouchListener() {
                    @Override
                    public boolean onTouch(View v, MotionEvent event) {
                        try {
                            return v.onTouchEvent(event);
                        } catch (ActivityNotFoundException e) {
                            // ignore
                            return true;
                        }
                    }
                });
            }
        }

        // Description
        if (description != null && description.length() != 0) {
            setTextCommon(view, R.id.description, description);
        }
        updateDescription ();  // Expand or collapse full description
    }

    private void sendAccessibilityEvent() {
        AccessibilityManager am =
            (AccessibilityManager) getActivity().getSystemService(Service.ACCESSIBILITY_SERVICE);
        if (!am.isEnabled()) {
            return;
        }

        AccessibilityEvent event = AccessibilityEvent.obtain(AccessibilityEvent.TYPE_VIEW_FOCUSED);
        event.setClassName(getClass().getName());
        event.setPackageName(getActivity().getPackageName());
        List<CharSequence> text = event.getText();

        addFieldToAccessibilityEvent(text, mTitle);
        addFieldToAccessibilityEvent(text, mWhen);
        addFieldToAccessibilityEvent(text, mWhere);
        addFieldToAccessibilityEvent(text, mWhat);
        addFieldToAccessibilityEvent(text, mAttendees);

        RadioGroup response = (RadioGroup) getView().findViewById(R.id.response_value);
        if (response.getVisibility() == View.VISIBLE) {
            int id = response.getCheckedRadioButtonId();
            if (id != View.NO_ID) {
                text.add(((TextView) getView().findViewById(R.id.response_label)).getText());
                text.add((((RadioButton) (response.findViewById(id))).getText() + PERIOD_SPACE));
            }
        }

        am.sendAccessibilityEvent(event);
    }

    /**
     * @param text
     */
    private void addFieldToAccessibilityEvent(List<CharSequence> text, TextView view) {
        if (view == null) {
            return;
        }
        String str = view.toString().trim();
        if (!TextUtils.isEmpty(str)) {
            text.add(mTitle.getText());
            text.add(PERIOD_SPACE);
        }
    }

    private void updateCalendar(View view) {
        mCalendarOwnerAccount = "";
        if (mCalendarsCursor != null && mEventCursor != null) {
            mCalendarsCursor.moveToFirst();
            String tempAccount = mCalendarsCursor.getString(CALENDARS_INDEX_OWNER_ACCOUNT);
            mCalendarOwnerAccount = (tempAccount == null) ? "" : tempAccount;
            mOwnerCanRespond = mCalendarsCursor.getInt(CALENDARS_INDEX_OWNER_CAN_RESPOND) != 0;

            String displayName = mCalendarsCursor.getString(CALENDARS_INDEX_DISPLAY_NAME);

            // start duplicate calendars query
            mHandler.startQuery(TOKEN_QUERY_DUPLICATE_CALENDARS, null, Calendars.CONTENT_URI,
                    CALENDARS_PROJECTION, CALENDARS_DUPLICATE_NAME_WHERE,
                    new String[] {displayName}, null);

            String eventOrganizer = mEventCursor.getString(EVENT_INDEX_ORGANIZER);
            mIsOrganizer = mCalendarOwnerAccount.equalsIgnoreCase(eventOrganizer);
            setTextCommon(view, R.id.organizer, eventOrganizer);
            if (!mIsOrganizer) {
                setVisibilityCommon(view, R.id.organizer_container, View.VISIBLE);
            } else {
                setVisibilityCommon(view, R.id.organizer_container, View.GONE);
            }
            mHasAttendeeData = mEventCursor.getInt(EVENT_INDEX_HAS_ATTENDEE_DATA) != 0;
            mCanModifyCalendar =
                    mEventCursor.getInt(EVENT_INDEX_ACCESS_LEVEL) >= Calendars.CAL_ACCESS_CONTRIBUTOR;
            mIsBusyFreeCalendar =
                    mEventCursor.getInt(EVENT_INDEX_ACCESS_LEVEL) == Calendars.CAL_ACCESS_FREEBUSY;

            if (!mIsBusyFreeCalendar) {
                Button b = (Button) mView.findViewById(R.id.edit);
                b.setEnabled(true);
                b.setOnClickListener(new OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        doEdit();
                        // For dialogs, just close the fragment
                        // For full screen, close activity on phone, leave it for tablet
                        if (mIsDialog) {
                            EventInfoFragment.this.dismiss();
                        }
                        else if (!mIsTabletConfig){
                            getActivity().finish();
                        }
                    }
                });
            }
            if (!mCanModifyCalendar) {
                if (mIsDialog) {
                    View button = mView.findViewById(R.id.delete);
                    if (button != null) {
                        button.setEnabled(false);
                        button.setVisibility(View.GONE);
                    }
                    button = mView.findViewById(R.id.edit);
                    if (button != null) {
                        button.setEnabled(false);
                        button.setVisibility(View.GONE);
                    }
                }
            }
            if (mMenu != null) {
                mActivity.invalidateOptionsMenu();
            }
        } else {
            setVisibilityCommon(view, R.id.calendar, View.GONE);
            sendAccessibilityEventIfQueryDone(TOKEN_QUERY_DUPLICATE_CALENDARS);
        }
    }

    /**
     *
     */
    private void updateMenu() {
        if (mMenu == null) {
            return;
        }
        MenuItem delete = mMenu.findItem(R.id.info_action_delete);
        MenuItem edit = mMenu.findItem(R.id.info_action_edit);
        if (delete != null) {
            delete.setVisible(mCanModifyCalendar);
            delete.setEnabled(mCanModifyCalendar);
        }
        if (edit != null) {
            edit.setVisible(mCanModifyCalendar);
            edit.setEnabled(mCanModifyCalendar);
        }
    }

    private void updateAttendees(View view) {

        if (mAcceptedAttendees.size() + mDeclinedAttendees.size() +
                mTentativeAttendees.size() + mNoResponseAttendees.size() > 0) {
            (mLongAttendees).addAttendees(mAcceptedAttendees);
            (mLongAttendees).addAttendees(mDeclinedAttendees);
            (mLongAttendees).addAttendees(mTentativeAttendees);
            (mLongAttendees).addAttendees(mNoResponseAttendees);
            mLongAttendees.setEnabled(false);
            mLongAttendees.setVisibility(View.VISIBLE);
        } else {
            mLongAttendees.setVisibility(View.GONE);
        }
    }

    public void initReminders(View view, Cursor cursor) {

        // Add reminders
        while (cursor.moveToNext()) {
            int minutes = cursor.getInt(EditEventHelper.REMINDERS_INDEX_MINUTES);
            int method = cursor.getInt(EditEventHelper.REMINDERS_INDEX_METHOD);
            mOriginalReminders.add(ReminderEntry.valueOf(minutes, method));
        }
        // Sort appropriately for display (by time, then type)
        Collections.sort(mOriginalReminders);

        // Load the labels and corresponding numeric values for the minutes and methods lists
        // from the assets.  If we're switching calendars, we need to clear and re-populate the
        // lists (which may have elements added and removed based on calendar properties).  This
        // is mostly relevant for "methods", since we shouldn't have any "minutes" values in a
        // new event that aren't in the default set.
        Resources r = mActivity.getResources();
        mReminderMinuteValues = loadIntegerArray(r, R.array.reminder_minutes_values);
        mReminderMinuteLabels = loadStringArray(r, R.array.reminder_minutes_labels);
        mReminderMethodValues = loadIntegerArray(r, R.array.reminder_methods_values);
        mReminderMethodLabels = loadStringArray(r, R.array.reminder_methods_labels);

        // Remove any reminder methods that aren't allowed for this calendar.  If this is
        // a new event, mCalendarAllowedReminders may not be set the first time we're called.
        if (mCalendarAllowedReminders != null) {
            EventViewUtils.reduceMethodList(mReminderMethodValues, mReminderMethodLabels,
                    mCalendarAllowedReminders);
        }

        if (mHasAlarm) {
            ArrayList<ReminderEntry> reminders = mOriginalReminders;
            // Insert any minute values that aren't represented in the minutes list.
            for (ReminderEntry re : reminders) {
                EventViewUtils.addMinutesToList(
                        mActivity, mReminderMinuteValues, mReminderMinuteLabels, re.getMinutes());
            }
            // Create a UI element for each reminder.  We display all of the reminders we get
            // from the provider, even if the count exceeds the calendar maximum.  (Also, for
            // a new event, we won't have a maxReminders value available.)
            for (ReminderEntry re : reminders) {
                      EventViewUtils.addReminder(mActivity, mScrollView, this, mReminderViews,
                              mReminderMinuteValues, mReminderMinuteLabels,
                              mReminderMethodValues, mReminderMethodLabels,
                              re, Integer.MAX_VALUE);
            }
        }
    }

    private void formatAttendees(ArrayList<Attendee> attendees, SpannableStringBuilder sb, int type) {
        if (attendees.size() <= 0) {
            return;
        }

        int begin = sb.length();
        boolean firstTime = sb.length() == 0;

        if (firstTime == false) {
            begin += 2; // skip over the ", " for formatting.
        }

        for (Attendee attendee : attendees) {
            if (firstTime) {
                firstTime = false;
            } else {
                sb.append(", ");
            }

            String name = attendee.getDisplayName();
            sb.append(name);
        }

        switch (type) {
            case Attendees.ATTENDEE_STATUS_ACCEPTED:
                break;
            case Attendees.ATTENDEE_STATUS_DECLINED:
                sb.setSpan(new StrikethroughSpan(), begin, sb.length(),
                        Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
                // fall through
            default:
                // The last INCLUSIVE causes the foreground color to be applied
                // to the rest of the span. If not, the comma at the end of the
                // declined or tentative may be black.
                sb.setSpan(new ForegroundColorSpan(0xFF999999), begin, sb.length(),
                        Spannable.SPAN_EXCLUSIVE_INCLUSIVE);
                break;
        }
    }

    void updateResponse(View view) {
        // we only let the user accept/reject/etc. a meeting if:
        // a) you can edit the event's containing calendar AND
        // b) you're not the organizer and only attendee AND
        // c) organizerCanRespond is enabled for the calendar
        // (if the attendee data has been hidden, the visible number of attendees
        // will be 1 -- the calendar owner's).
        // (there are more cases involved to be 100% accurate, such as
        // paying attention to whether or not an attendee status was
        // included in the feed, but we're currently omitting those corner cases
        // for simplicity).

        // TODO Switch to EditEventHelper.canRespond when this class uses CalendarEventModel.
        if (!mCanModifyCalendar || (mHasAttendeeData && mIsOrganizer && mNumOfAttendees <= 1) ||
                (mIsOrganizer && !mOwnerCanRespond)) {
            setVisibilityCommon(view, R.id.response_container, View.GONE);
            return;
        }

        setVisibilityCommon(view, R.id.response_container, View.VISIBLE);


        int response;
        if (mAttendeeResponseFromIntent != CalendarController.ATTENDEE_NO_RESPONSE) {
            response = mAttendeeResponseFromIntent;
        } else {
            response = mOriginalAttendeeResponse;
        }

        int buttonToCheck = findButtonIdForResponse(response);
        RadioGroup radioGroup = (RadioGroup) view.findViewById(R.id.response_value);
        radioGroup.check(buttonToCheck); // -1 clear all radio buttons
        radioGroup.setOnCheckedChangeListener(this);
    }

    private void setTextCommon(View view, int id, CharSequence text) {
        TextView textView = (TextView) view.findViewById(id);
        if (textView == null)
            return;
        textView.setText(text);
    }

    private void setVisibilityCommon(View view, int id, int visibility) {
        View v = view.findViewById(id);
        if (v != null) {
            v.setVisibility(visibility);
        }
        return;
    }

    /**
     * Taken from com.google.android.gm.HtmlConversationActivity
     *
     * Send the intent that shows the Contact info corresponding to the email address.
     */
    public void showContactInfo(Attendee attendee, Rect rect) {
        // First perform lookup query to find existing contact
        final ContentResolver resolver = getActivity().getContentResolver();
        final String address = attendee.mEmail;
        final Uri dataUri = Uri.withAppendedPath(CommonDataKinds.Email.CONTENT_FILTER_URI,
                Uri.encode(address));
        final Uri lookupUri = ContactsContract.Data.getContactLookupUri(resolver, dataUri);

        if (lookupUri != null) {
            // Found matching contact, trigger QuickContact
            QuickContact.showQuickContact(getActivity(), rect, lookupUri,
                    QuickContact.MODE_MEDIUM, null);
        } else {
            // No matching contact, ask user to create one
            final Uri mailUri = Uri.fromParts("mailto", address, null);
            final Intent intent = new Intent(Intents.SHOW_OR_CREATE_CONTACT, mailUri);

            // Pass along full E-mail string for possible create dialog
            Rfc822Token sender = new Rfc822Token(attendee.mName, attendee.mEmail, null);
            intent.putExtra(Intents.EXTRA_CREATE_DESCRIPTION, sender.toString());

            // Only provide personal name hint if we have one
            final String senderPersonal = attendee.mName;
            if (!TextUtils.isEmpty(senderPersonal)) {
                intent.putExtra(Intents.Insert.NAME, senderPersonal);
            }

            startActivity(intent);
        }
    }

    @Override
    public void onPause() {
        mIsPaused = true;
        mHandler.removeCallbacks(onDeleteRunnable);
        super.onPause();
    }

    @Override
    public void onResume() {
        super.onResume();
        mIsPaused = false;
        if (mDismissOnResume) {
            mHandler.post(onDeleteRunnable);
        }
    }

    @Override
    public void eventsChanged() {
    }

    @Override
    public long getSupportedEventTypes() {
        return EventType.EVENTS_CHANGED;
    }

    @Override
    public void handleEvent(EventInfo event) {
        if (event.eventType == EventType.EVENTS_CHANGED && mHandler != null) {
            // reload the data
            mHandler.startQuery(TOKEN_QUERY_EVENT, null, mUri, EVENT_PROJECTION,
                    null, null, null);
        }

    }


    @Override
    public void onClick(View view) {

        // This must be a click on one of the "remove reminder" buttons
        LinearLayout reminderItem = (LinearLayout) view.getParent();
        LinearLayout parent = (LinearLayout) reminderItem.getParent();
        parent.removeView(reminderItem);
        mReminderViews.remove(reminderItem);
    }


    /**
     * Add a new reminder when the user hits the "add reminder" button.  We use the default
     * reminder time and method.
     */
    private void addReminder() {
        // TODO: when adding a new reminder, make it different from the
        // last one in the list (if any).
        if (mDefaultReminderMinutes == GeneralPreferences.NO_REMINDER) {
            EventViewUtils.addReminder(mActivity, mScrollView, this, mReminderViews,
                    mReminderMinuteValues, mReminderMinuteLabels,
                    mReminderMethodValues, mReminderMethodLabels,
                    ReminderEntry.valueOf(GeneralPreferences.REMINDER_DEFAULT_TIME),
                    mMaxReminders);
        } else {
            EventViewUtils.addReminder(mActivity, mScrollView, this, mReminderViews,
                    mReminderMinuteValues, mReminderMinuteLabels,
                    mReminderMethodValues, mReminderMethodLabels,
                    ReminderEntry.valueOf(mDefaultReminderMinutes),
                    mMaxReminders);
        }
    }


    private void prepareReminders() {
        Resources r = mActivity.getResources();
        mReminderMinuteValues = loadIntegerArray(r, R.array.reminder_minutes_values);
        mReminderMinuteLabels = loadStringArray(r, R.array.reminder_minutes_labels);
        mReminderMethodValues = loadIntegerArray(r, R.array.reminder_methods_values);
        mReminderMethodLabels = loadStringArray(r, R.array.reminder_methods_labels);
    }


    private boolean saveReminders() {
        ArrayList<ContentProviderOperation> ops = new ArrayList<ContentProviderOperation>(3);

        // Read reminders from UI
        mReminders = EventViewUtils.reminderItemsToReminders(mReminderViews,
                mReminderMinuteValues, mReminderMethodValues);

        // Check if there are any changes in the reminder
        boolean changed = EditEventHelper.saveReminders(ops, mEventId, mReminders,
                mOriginalReminders, false /* no force save */);

        if (!changed) {
            return false;
        }

        // save new reminders
        AsyncQueryService service = new AsyncQueryService(getActivity());
        service.startBatch(0, null, Calendars.CONTENT_URI.getAuthority(), ops, 0);
        // Update the "hasAlarm" field for the event
        Uri uri = ContentUris.withAppendedId(Events.CONTENT_URI, mEventId);
        int len = mReminders.size();
        boolean hasAlarm = len > 0;
        if (hasAlarm != mHasAlarm) {
            ContentValues values = new ContentValues();
            values.put(Events.HAS_ALARM, hasAlarm ? 1 : 0);
            service.startUpdate(0, null, uri, values, null, null, 0);
        }
        return true;
    }

    /**
     * Loads an integer array asset into a list.
     */
    private static ArrayList<Integer> loadIntegerArray(Resources r, int resNum) {
        int[] vals = r.getIntArray(resNum);
        int size = vals.length;
        ArrayList<Integer> list = new ArrayList<Integer>(size);

        for (int i = 0; i < size; i++) {
            list.add(vals[i]);
        }

        return list;
    }
    /**
     * Loads a String array asset into a list.
     */
    private static ArrayList<String> loadStringArray(Resources r, int resNum) {
        String[] labels = r.getStringArray(resNum);
        ArrayList<String> list = new ArrayList<String>(Arrays.asList(labels));
        return list;
    }

}