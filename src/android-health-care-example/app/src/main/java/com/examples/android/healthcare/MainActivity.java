package com.examples.android.healthcare;

import static com.examples.android.healthcare.functions.MyLogging.DEBUG_OUT;

import android.content.Intent;
import android.os.Bundle;

import com.examples.android.healthcare.databinding.ActivityMainBinding;
import com.examples.android.healthcare.ui.main.AppTopFragment;
import com.examples.android.healthcare.ui.main.MultiScreenFragmentAdapter;

import androidx.appcompat.app.AppCompatActivity;

import android.text.TextUtils;
import android.util.Log;

import androidx.viewpager2.widget.ViewPager2;
import androidx.navigation.ui.AppBarConfiguration;

import android.view.Menu;
import android.view.MenuItem;

public class MainActivity extends AppCompatActivity {
    private static final String TAG = MainActivity.class.getSimpleName();

    private static final int FRAGMENT_APPTOP = 0;
    private AppBarConfiguration appBarConfiguration;
    private ActivityMainBinding binding;

    MultiScreenFragmentAdapter mFragmentAdapter;
    ViewPager2 mViewPager2;
    ViewPager2.OnPageChangeCallback mOnPageChangeCallback;
//    private AppBarConfiguration appBarConfiguration;
//    private ActivityMainBinding binding;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        mViewPager2 = findViewById(R.id.pager);
        mFragmentAdapter = new MultiScreenFragmentAdapter(
                getSupportFragmentManager(),
                getLifecycle()
        );

        // トップ画面フラグメント
        mFragmentAdapter.addFragment(AppTopFragment.newInstance());
        mViewPager2.setOrientation(ViewPager2.ORIENTATION_HORIZONTAL);
        mViewPager2.setAdapter(mFragmentAdapter);
    }

    @Override
    public void onStart() {
        super.onStart();
        DEBUG_OUT.accept(TAG, "onStart()");

        // ページャの切り替わりをモニタするコールバック
        mOnPageChangeCallback = new ViewPager2.OnPageChangeCallback() {
            @Override
            public void onPageSelected(int position) {
                super.onPageSelected(position);
                Log.d(TAG, "position: " + position);
            }
        };
        // ページャにコールバックを登録
        mViewPager2.registerOnPageChangeCallback(mOnPageChangeCallback);
    }

    @Override
    public void onStop() {
        super.onStop();
        DEBUG_OUT.accept(TAG, "onStop()");

        // ページャコールバックの登録解除
        if (mOnPageChangeCallback != null) {
            mViewPager2.unregisterOnPageChangeCallback(mOnPageChangeCallback);
            mOnPageChangeCallback = null;
        }
    }

    @Override
    public void onBackPressed() {
        DEBUG_OUT.accept(TAG, "onBackPressed()");

        if (mOnPageChangeCallback != null) {
            int currentItem = mViewPager2.getCurrentItem();
            if (currentItem == FRAGMENT_APPTOP) {
                // 登録画面でバックキー押下なら最近の画面に残さない
                // https://developer.android.com/guide/components/activities/recents?hl=ja#java
                // 最近の画面
                // [最近] 画面は、最近アクセスしたアクティビティとタスクを一覧表示するシステムレベルの UI です
                // The document is no longer needed; remove its task.
                // AppTask クラスを使用したタスクの削除
                DEBUG_OUT.accept(TAG, "finishAndRemoveTask()");
                finishAndRemoveTask();
            } else {
                DEBUG_OUT.accept(TAG, "super.onBackPressed()");
                super.onBackPressed();
            }
        }
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        // Inflate the menu; this adds items to the action bar if it is present.
        getMenuInflater().inflate(R.menu.menu_main, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        int id = item.getItemId();

        //noinspection SimplifiableIfStatement
        if (id == R.id.actionHealthcareSettings) {
            showSettingsActivity();
            return true;
        }

        return super.onOptionsItemSelected(item);
    }

    private void showSettingsActivity() {
        Intent settingsIntent = new Intent(this, SettingsActivity.class);
        startActivity(settingsIntent);
    }

}
