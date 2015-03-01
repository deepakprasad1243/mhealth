package com.example.jack.brainwaves;

import android.annotation.TargetApi;
import android.app.Activity;
import android.content.SharedPreferences;
import android.content.pm.ActivityInfo;
import android.graphics.Color;
import android.os.Build;
import android.os.Bundle;
import android.view.View;
import android.view.Menu;
import android.view.MenuItem;
import android.content.Intent;
import android.view.Window;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.TextView;

import com.androidplot.pie.PieChart;
import com.androidplot.pie.PieRenderer;
import com.androidplot.pie.Segment;
import com.androidplot.pie.SegmentFormatter;

import java.util.Observable;
import java.util.Observer;

public class MainActivity extends Activity {

    public final static String EXTRA_MESSAGE = "com.mycompany.myfirstapp.MESSAGE";

    private TextView donutSizeTextView;
    private PieChart pie;
    private static final int[] buttonids = {
            R.id.startDynamicXYExButton,
            R.id.startOrSensorExButton,
            R.id.museandpssbtn,
    };

    private Segment s1;
    private Segment s2;
    SegmentFormatter sf1 = new SegmentFormatter();
    SegmentFormatter sf2 = new SegmentFormatter();

    private float goldenPercentageOutput = .96f;
    private float dynamicPercentage;

    private Thread myThread;

    private DynamicScaleShow data;

    int dynamicColorR = 255;
    int dynamicColorG = 80;
    int dynamicColorB = 80;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_PORTRAIT);

        Button startDynamicXYExButton = (Button)findViewById(R.id.startDynamicXYExButton);
        startDynamicXYExButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                startActivity(new Intent(MainActivity.this, DynamicXYPlotActivity.class));
            }
        });

        Button startOrSensorExButton = (Button) findViewById(R.id.startOrSensorExButton);
        startOrSensorExButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                startActivity(new Intent(MainActivity.this, OrientationSensorExampleActivity.class));
            }
        });

        Button startMuseButton = (Button) findViewById(R.id.museandpssbtn);
        startMuseButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                startActivity(new Intent(MainActivity.this, MuseActivity.class));
            }
        });

        TextView startRefeshAnimation = (TextView) findViewById(R.id.donutSizeTextView);
        startRefeshAnimation.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                data.stopThread();
                data.setReplay(true);
                myThread = new Thread(data);
                myThread.start();
            }
        });

        dynamicColorR = 255;
        dynamicColorG = 80;
        dynamicColorB = 80;
        dynamicPercentage = .0f;

        // initialize our XYPlot reference:
        pie = (PieChart) findViewById(R.id.mySimplePieChart);

        donutSizeTextView = (TextView) findViewById(R.id.donutSizeTextView);
        updateDonutText();
        updateDonutColor();

        s1 = new Segment("", 20);
        s2 = new Segment("", 50);

        sf1.configure(getApplicationContext(), R.xml.pie_segment_formatter1);
        sf1.getFillPaint().setColor(dynamicColor());
        sf2.configure(getApplicationContext(), R.xml.pie_segment_formatter2);

        pie.setPlotMarginBottom(0);
        pie.addSegment(s1, sf1);
        pie.addSegment(s2, sf2);
        pie.getBorderPaint().setColor(Color.TRANSPARENT);
        pie.getBackgroundPaint().setColor(Color.TRANSPARENT);
        pie.getRenderer(PieRenderer.class).setDonutSize(.80f, PieRenderer.DonutMode.PERCENT);
        pie.redraw();

        data = new DynamicScaleShow();
    }

    @Override
    public void onResume() {
        // kick off the data generating thread:
        SharedPreferences settings = this.getSharedPreferences("appInfo", 0);
        boolean replayAnimation = settings.getBoolean("replay_animation", true);
        data.setReplay(replayAnimation);
        // Only play animation when necessary
        if (replayAnimation) {
            SharedPreferences.Editor editor = settings.edit();
            editor.putBoolean("replay_animation", false);
            editor.commit();
        }
        myThread = new Thread(data);
        myThread.start();
        super.onResume();
    }

    @Override
    public void onPause() {
        data.stopThread();
        super.onPause();
    }

    class DynamicScaleShow extends Activity implements Runnable {

        private boolean replay;

        public void setReplay(boolean replay) {
            this.replay = replay;
        }

        // encapsulates management of the observers watching this datasource for update events:
        class MyObservable extends Observable {
            @Override
            public void notifyObservers() {
                setChanged();
                super.notifyObservers();
            }
        }

        private MyObservable notifier;
        private boolean keepRunning = false;

        DynamicScaleShow() {
            notifier = new MyObservable();
        }

        public void stopThread() {
            keepRunning = false;
        }

        //@Override
        public void run() {
            try {
                keepRunning = true;
                s1.setValue(0);
                dynamicPercentage = replay ? .0f : goldenPercentageOutput;
                float diff = Math.abs(dynamicPercentage - goldenPercentageOutput);
                while(dynamicPercentage <= goldenPercentageOutput && keepRunning) {
                    Thread.sleep(10);
                    s1.setValue(s2.getValue().floatValue() * dynamicPercentage / (1 - dynamicPercentage));
                    dynamicPercentage += 0.001f * diff + 0.0005f;
                    diff = Math.abs(dynamicPercentage - goldenPercentageOutput);
                    updatePie();
                    updateUIInUIThread();    // This has to be done in UI Thread!!
                }
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        }

        protected void updateUIInUIThread() {
            donutSizeTextView.post(new Runnable() {
                @Override
                public void run() {
                    updateDonutText();
                    updateDonutColor();
                    updateButtonsColor();
                }
            });
        }

        public void addObserver(Observer observer) {
            notifier.addObserver(observer);
        }

        public void removeObserver(Observer observer) {
            notifier.deleteObserver(observer);
        }
    }

    protected void updatePie() {
        sf1.getFillPaint().setColor(dynamicColor());
        pie.redraw();
    }

    protected void updateDonutText() {
        donutSizeTextView.setText(String.format("%.0f", dynamicPercentage * 100));
    }

    protected void updateDonutColor() {
        donutSizeTextView.setTextColor(dynamicColor());
    }

    protected void updateButtonsColor() {
        for(int i : buttonids) {
            Button b = (Button) findViewById(i);
            b.setBackgroundColor(dynamicColor());
        }
    }

    protected int dynamicColor() {
        Color c = new Color();
        return c.rgb(
                Math.round(dynamicColorR * dynamicPercentage * 0.35f) + 102,
                Math.round(dynamicColorG * (1 - dynamicPercentage) * 0.30f) + 60,
                Math.round(dynamicColorB * (1 - dynamicPercentage) * 0.30f) + 60
        );
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        // Inflate the menu; this adds items to the action bar if it is present.
        getMenuInflater().inflate(R.menu.menu_main, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        // Handle action bar item clicks here. The action bar will
        // automatically handle clicks on the Home/Up button, so long
        // as you specify a parent activity in AndroidManifest.xml.
        int id = item.getItemId();

        //noinspection SimplifiableIfStatement
        if (id == R.id.action_settings) {
            return true;
        }

        return super.onOptionsItemSelected(item);
    }
}