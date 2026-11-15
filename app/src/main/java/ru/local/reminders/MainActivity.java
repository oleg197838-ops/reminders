package ru.local.reminders;

import android.app.*;
import android.content.*;
import android.net.Uri;
import android.os.*;
import android.provider.Settings;
import android.view.*;
import android.widget.*;

import org.json.*;

import java.text.SimpleDateFormat;
import java.util.*;

public class MainActivity extends Activity {

    LinearLayout root, list;
    TextView status;

    static final String[] DAYS = {
        "Пн", "Вт", "Ср", "Чт", "Пт", "Сб", "Вс"
    };

    static JSONArray load(Context c) {
        try {
            String data = c.getSharedPreferences("data", 0)
                    .getString("items", "[]");
            return new JSONArray(data);
        } catch (Exception e) {
            return new JSONArray();
        }
    }

    static void save(Context c, JSONArray items) {
        c.getSharedPreferences("data", 0).edit()
                .putString("items", items.toString()).commit();
    }

    static void put(Context c, JSONObject item) {
        JSONArray old = load(c);
        JSONArray result = new JSONArray();
        boolean found = false;

        for (int i = 0; i < old.length(); i++) {
            JSONObject current = old.optJSONObject(i);

            if (current.optInt("id") == item.optInt("id")) {
                result.put(item);
                found = true;
            } else {
                result.put(current);
            }
        }

        if (!found) result.put(item);
        save(c, result);
    }

    static PendingIntent alarmIntent(Context c, int id) {
        Intent intent = new Intent(c, AlarmReceiver.class);
        intent.putExtra("id", id);

        return PendingIntent.getBroadcast(
                c, id, intent,
                PendingIntent.FLAG_UPDATE_CURRENT
                        | PendingIntent.FLAG_IMMUTABLE
        );
    }

    static void cancel(Context c, int id) {
        AlarmManager manager =
                (AlarmManager) c.getSystemService(ALARM_SERVICE);
        manager.cancel(alarmIntent(c, id));
    }

    static long nextTime(JSONObject item) {
        long now = System.currentTimeMillis();
        int days = item.optInt("days");

        if (days == 0) {
            return Math.max(item.optLong("time"), now + 1500);
        }

        Calendar base = Calendar.getInstance();

        for (int i = 0; i < 8; i++) {
            Calendar candidate = (Calendar) base.clone();
            candidate.add(Calendar.DATE, i);
            candidate.set(Calendar.HOUR_OF_DAY, item.optInt("hour"));
            candidate.set(Calendar.MINUTE, item.optInt("minute"));
            candidate.set(Calendar.SECOND, 0);
            candidate.set(Calendar.MILLISECOND, 0);

            int day = (candidate.get(Calendar.DAY_OF_WEEK) + 5) % 7;

            if ((days & (1 << day)) != 0
                    && candidate.getTimeInMillis() > now) {
                return candidate.getTimeInMillis();
            }
        }

        return now + 86400000L;
    }

    static void schedule(Context c, JSONObject item) {
        int id = item.optInt("id");
        cancel(c, id);

        if (item.optBoolean("done")) return;

        AlarmManager manager =
                (AlarmManager) c.getSystemService(ALARM_SERVICE);

        long time = nextTime(item);
        PendingIntent pending = alarmIntent(c, id);

        try {
            if (Build.VERSION.SDK_INT < 31
                    || manager.canScheduleExactAlarms()) {
                manager.setExactAndAllowWhileIdle(
                        AlarmManager.RTC_WAKEUP, time, pending);
            } else {
                manager.setAndAllowWhileIdle(
                        AlarmManager.RTC_WAKEUP, time, pending);
            }
        } catch (SecurityException e) {
            manager.setAndAllowWhileIdle(
                    AlarmManager.RTC_WAKEUP, time, pending);
        }
    }

    static void restore(Context c) {
        JSONArray items = load(c);

        for (int i = 0; i < items.length(); i++) {
            schedule(c, items.optJSONObject(i));
        }
    }

    int dp(int value) {
        return (int) (value * getResources()
                .getDisplayMetrics().density);
    }

    TextView text(String value, int size) {
        TextView view = new TextView(this);
        view.setText(value);
        view.setTextSize(size);
        view.setPadding(0, dp(7), 0, dp(7));
        return view;
    }

    Button button(
            LinearLayout parent,
            String label,
            View.OnClickListener listener
    ) {
        Button view = new Button(this);
        view.setText(label);
        view.setOnClickListener(listener);
        parent.addView(view);
        return view;
    }

    @Override
    public void onCreate(Bundle savedState) {
        super.onCreate(savedState);

        root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(16), dp(16), dp(16), dp(16));

        if (Build.VERSION.SDK_INT >= 30) {
            root.setOnApplyWindowInsetsListener((view, insets) -> {
                android.graphics.Insets bars =
                        insets.getInsets(WindowInsets.Type.systemBars());

                view.setPadding(
                        dp(16) + bars.left,
                        dp(12) + bars.top,
                        dp(16) + bars.right,
                        dp(12) + bars.bottom
                );
                return insets;
            });
        }

        setContentView(root);
        root.addView(text("Напоминания", 28));

        status = text("", 14);
        root.addView(status);

        button(
