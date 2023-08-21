package com.examples.android.healthcare;

import static com.examples.android.healthcare.functions.MyLogging.DEBUG_OUT;

import android.content.Intent;
import android.os.Bundle;

import com.examples.android.healthcare.ui.main.AppBloodPressFragment;
import com.examples.android.healthcare.ui.main.AppSleepManFragment;
import com.examples.android.healthcare.ui.main.AppTopFragment;
import com.examples.android.healthcare.ui.main.MultiScreenFragmentAdapter;

import androidx.appcompat.app.AppCompatActivity;
import androidx.viewpager2.widget.ViewPager2;

import android.view.Menu;
import android.view.MenuItem;

/**
 * アプリケーションメイン画面
 */
public class MainActivity extends AppCompatActivity {
    private static final String TAG = MainActivity.class.getSimpleName();

    // トップ登録データ入力画面
    private static final int FRAGMENT_APP_TOP = 0;
    // 血圧測定データグラフ表示画面
    private static final int FRAGMENT_BLOOD_PRESS = 1;
    // 睡眠管理データグラフ表示画面
    private static final int FRAGMENT_SLEEP_MAN = 2;
//    private AppBarConfiguration appBarConfiguration;

    MultiScreenFragmentAdapter mFragmentAdapter;
    ViewPager2 mViewPager2;
    ViewPager2.OnPageChangeCallback mOnPageChangeCallback;

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
        mFragmentAdapter.addFragment(AppTopFragment.newInstance(FRAGMENT_APP_TOP));
        // 血圧測定データ画像表示フラグメント
        mFragmentAdapter.addFragment(AppBloodPressFragment.newInstance(FRAGMENT_BLOOD_PRESS));
        // 睡眠管理データ画像表示フラグメント
        mFragmentAdapter.addFragment(AppSleepManFragment.newInstance(FRAGMENT_SLEEP_MAN));
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
                DEBUG_OUT.accept(TAG, "position: " + position);
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
            // https://developer.android.com/develop/ui/views/animations/screen-slide-2
            //  Slide between fragments using ViewPager2
            int currentItem = mViewPager2.getCurrentItem();
            if (currentItem == FRAGMENT_APP_TOP) {
                // 登録画面でバックキー押下なら最近の画面に残さない
                // https://developer.android.com/guide/components/activities/recents?hl=ja#java
                // 最近の画面
                // [最近] 画面は、最近アクセスしたアクティビティとタスクを一覧表示するシステムレベルの UI です
                // The document is no longer needed; remove its task.
                // AppTask クラスを使用したタスクの削除
                DEBUG_OUT.accept(TAG, "finishAndRemoveTask()");
                finishAndRemoveTask();
            } else if (currentItem > FRAGMENT_APP_TOP){
                // 画像表示フラグメントの場合
                DEBUG_OUT.accept(TAG, "Back previous fragment.");
                // 一つ前に戻る
                mViewPager2.setCurrentItem(mViewPager2.getCurrentItem() - 1);
                // これはアプリが終了してしまう
                // super.onBackPressed();
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
