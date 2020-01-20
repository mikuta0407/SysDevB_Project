package com.mikuta0407.presentation_timer;

import androidx.appcompat.app.AppCompatActivity;

import android.content.res.ColorStateList;
import android.media.AudioAttributes;
import android.os.Bundle;
import android.os.CountDownTimer;
import android.util.Log;
import android.view.View;
import android.widget.ProgressBar;
import android.widget.RadioGroup;
import android.widget.Switch;
import android.widget.TextView;
import android.media.AudioManager;
import android.media.SoundPool;

import com.google.android.material.floatingactionbutton.FloatingActionButton;

import java.text.SimpleDateFormat;

/*
	発表用タイマー
	デモ用設定:
		発表10分→20秒
		質問5分→15秒
		1分前アラーム→10秒前アラーム

 */

public class MainActivity extends AppCompatActivity {

	boolean run = false; //タイマーが動作中かそうじゃないかを記録
	boolean finished = true; //一時停止状態か、終了/キャンセルで止まったのかを記録。
	boolean paused = false; //一時停止状態の記録
	int mode = 1; //1: 発表 2: 質問 3: 点滅用
	boolean flashmode = true;   //点滅の反転。本当はflashpartsに書くべきなのだが、回転対策のためここに記載。
	boolean alerm = true;
	boolean rang = false;

	boolean recovered = false; // 再生成かどうかの判定

	private TextView timerText; //数字表示部のTextView
	private TextView pastText; //経過時間のTextView
	private ProgressBar timeProgressBar; //プログレスバー
	private FloatingActionButton start_pause;   //スタートストップボタン
	private FloatingActionButton cancel;    //キャンセルボタン
	private Switch alermSwitch;
	private CountDownTimer countDown; //カウントダウンクラス
	private CountDownTimer flashCountDown;
	private SimpleDateFormat dataFormat = new SimpleDateFormat("mm:ss.S", java.util.Locale.JAPANESE); //データフォーマット
	private RadioGroup ptime_radiobox;  //発表時間設定用ラジオボタン
	private RadioGroup qtime_radiobox;  //質問時間設定用ラジオボタン


	private SoundPool soundPool;
	private int lastOneMinSound;
	private int ptimeEndSound;
	private int qtimeEndSound;


	//public long ptime = 20000; //発表時間設定を記録 //ptime = 600000; //一度も動作させたことがないときにActivityが再生成されるとptimeが何もなくて虚無になるので
	public long ptime;
	public long qtime; //質問時間設定を記録
	private long leftTime; //残り時間を記録
	private long flashTime; //点滅用(回転対策にここに記載)


	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.activity_main);

		/* いろいろ定義たいむ */

		//スタートボタン
		start_pause = findViewById(R.id.start_pause);
		start_pause.setBackgroundTintList(ColorStateList.valueOf(getResources().getColor(R.color.tcu)));

		//キャンセルボタン
		cancel = findViewById(R.id.cancel);

		//ラジオボタン
		ptime_radiobox = (RadioGroup)findViewById(R.id.ptime_radiobox);
		qtime_radiobox = (RadioGroup)findViewById(R.id.qtime_radiobox);

		//テキスト表示
		timerText = findViewById(R.id.disp_time);
		//timerText.setText("10:00.0");
		timerText.setText("00:20.0"); //デモ用
		pastText = findViewById(R.id.past_time);
		pastText.setText(dataFormat.format(0));

		//プログレスバー
		timeProgressBar = findViewById(R.id.progressBar);
		timeProgressBar.setProgress(100);

		//スイッチ
		alermSwitch = (Switch)findViewById(R.id.alerm_switch);

		//SoundPool
		final AudioAttributes audioAttributes = new AudioAttributes.Builder()
				// USAGE_MEDIA
				// USAGE_GAME
				.setUsage(AudioAttributes.USAGE_GAME)
				// CONTENT_TYPE_MUSIC
				// CONTENT_TYPE_SPEECH, etc.
				.setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
				.build();

		soundPool = new SoundPool.Builder()
				.setAudioAttributes(audioAttributes)
				// ストリーム数に応じて
				.setMaxStreams(3)
				.build();

		// ファイル準備
		lastOneMinSound = soundPool.load(this, R.raw.last_one_min_sound, 1);
		ptimeEndSound = soundPool.load(this, R.raw.ptime_end_sound, 1);
		qtimeEndSound = soundPool.load(this, R.raw.qtime_end_sound, 1);


		/* 回転時状態復元 */
		// savedInstanceStateがnullでないときは、Activityが再作成されたと判断、状態を復元
			if (savedInstanceState!=null) {

				Log.i("デバッグ", "状態復元作業に入ります。");
				recovered = true;

				// まず動作状態を復元
				run = savedInstanceState.getBoolean("runstatus");
				finished = savedInstanceState.getBoolean("finishedstatus");
				paused = savedInstanceState.getBoolean("pausedstatus");
				mode = savedInstanceState.getInt("modestatus");
				alerm = savedInstanceState.getBoolean("alermstatus");
					alermSwitch.setChecked(alerm);
				rang = savedInstanceState.getBoolean("rangstatus");

				//数値系
				ptime = savedInstanceState.getLong("ptimestatus");
				qtime = savedInstanceState.getLong("qtimestatus");
				leftTime = savedInstanceState.getLong("leftTimestatus");
				flashTime = savedInstanceState.getLong("flashTimestatus");

				//UI系

				//色復元
				if (mode == 1){
					start_pause.setBackgroundTintList(ColorStateList.valueOf(getResources().getColor(R.color.tcu)));
					timeProgressBar.setBackgroundTintList(ColorStateList.valueOf(getResources().getColor(R.color.tcu)));
				} else if (mode == 2 || mode == 3) {
					start_pause.setBackgroundTintList(ColorStateList.valueOf(getResources().getColor(R.color.tokyu)));
					timeProgressBar.setBackgroundTintList(ColorStateList.valueOf(getResources().getColor(R.color.tokyu)));
				}


				//状態別復元
				if (run) { // 動作中だったら
					start_pause.setImageDrawable(getResources().getDrawable(R.drawable.ic_pause)); //一時停止ボタンに変更.

					//タイマーか点滅か
					if (mode == 1 || mode == 2) {
						startTimer(); //タイマー続行
					} else if (mode == 3){
						flash();
					}

					//ラジオボタンの無効化
					radioEnabledFalse();

				} else {   // 動いてなかったら
					start_pause.setImageDrawable(getResources().getDrawable(R.drawable.ic_play)); //再生ボタンに変更

					if (paused) { //一時停止だった場合
						//数字復元
						updateCountDownText(); //一時停止時に回転した場合の文字復元
					}

					if (finished) { //停止中だった場合
						recovered = false;
						pastText.setText("00:00.0"); //除算の余りの関係で00:00.1になるので

						//停止時の文字復元
						//if (ptime == 600000) {
						if (ptime == 20000) { //デモ用
							//timerText.setText("10:00.0");
							timerText.setText("00:20.0"); //デモ用
						} else if (ptime == 1200000) {
							timerText.setText("20:00.0");
						} else if (ptime == 1800000) {
							timerText.setText("30:00.0");
						}
					}
				}

			}

			// ラジオボタン
			// 発表時間
			//if (ptime == 600000) {
			if (ptime == 20000) { //デモ用
				ptime_radiobox.check(R.id.ptime10);
			} else if (ptime == 1200000) {
				ptime_radiobox.check(R.id.ptime20);
			} else if (ptime == 1800000) {
				ptime_radiobox.check(R.id.ptime30);
			}
			// 質問時間
			//if (qtime == 300000) {動いてくれてるんですよね
			if (qtime == 15000) { //デモ用
				qtime_radiobox.check(R.id.qtime5);
			} else if (qtime == 600000) {
				qtime_radiobox.check(R.id.qtime10);
			}
		}


		// 以下メイン処理
		alerm = alermSwitch.isChecked();

		// スタート・ストップボタンが押されたら
		start_pause.setOnClickListener(new View.OnClickListener(){
			@Override
			public void onClick(View spClick) {
				if (run) { //動作中なら
					if (mode == 1 || mode == 2){
						start_pause.setImageDrawable(getResources().getDrawable(R.drawable.ic_play)); //再生ボタンに変更
						pauseTimer();
					} else if (mode == 3 || mode == 4){
						Log.i("デバッグ","点滅中なので何もしませんよ");
					}
				} else {        // 動いてなかったら
					start_pause.setImageDrawable(getResources().getDrawable(R.drawable.ic_pause)); //一時停止ボタンに変更

					if (finished == true){ //完了後または初期状態の場合
						// 発表時間設定の選択を取得
						int ptimeId = ptime_radiobox.getCheckedRadioButtonId();
						// ptimeに時間設定。(ms)
						if (ptimeId == R.id.ptime10) {
							//ptime = 600000;  //10*60*1000 ms
							ptime = 20000; //デモ用
						} else if (ptimeId == R.id.ptime20) {
							ptime = 1200000; //20*60*1000 ms
						} else if (ptimeId == R.id.ptime30) {
							ptime = 1800000; //30*60*1000 ms
						}


						// 質問時間設定の選択を取得

						int qtimeId = qtime_radiobox.getCheckedRadioButtonId();
						// qtimeに時間設定。(ms)
						if (qtimeId == R.id.qtime5) {
							//qtime = 300000;  //5*60*1000 ms
							qtime = 15000; //デモ用
						} else if (qtimeId == R.id.qtime10) {
							qtime = 600000;  //10*60*1000 ms
						}

					}
					radioEnabledFalse();
					Log.i("デバッグ", "ラジオボタンを無効化します(ボタン押したとき)");
					startTimer(); // タイマースタート
				}
			}
		});

		// キャンセルボタンが押されたら
		cancel.setOnClickListener(new View.OnClickListener(){
			@Override
			public void onClick(View v){
				resetTimer();
				start_pause.setImageDrawable(getResources().getDrawable(R.drawable.ic_play)); //再生ボタンに変更
				ptime_radiobox.setEnabled(true);
				//String resetFormatted = String.format(java.util.Locale.JAPANESE, "%02d:%02d.%01d", 0, 0, 0);
				//timerText.setText(resetFormatted);
				//pastText.setText(resetFormatted);
			}
		});
	}

	private void startTimer() {  // タイマー実行

		Log.i("デバッグ", "startTimerを叩きました。startTimer内でのptimeは "+ ptime);
		Log.i("デバッグ", "startTimerを叩きました。startTimer内でのmodeは "+ mode);
		run = true;
		if (paused == true){ //一時停止復帰
			paused = false;
			Log.i("デバッグ", "一時停止から復帰しましたわよ");
		} else if (recovered){
			recovered = false;
			Log.i("デバッグ", "startTimerは回転修正モードで起動しました");
		} else { // ノーマル起動
			Log.i("デバッグ", "startTimerはノーマルモードで起動しました");
			if (mode == 1) {
				leftTime = ptime;
				Log.i("デバッグ", "leftTimeに値を代入しました。ptimeは"+ ptime +"、leftTimeは" + leftTime);
			} else if (mode == 2) {
				leftTime = qtime;
			}
		}

		Log.i("デバッグ", "countDownTimerを起動します。leftTimeは "+ leftTime);

		countDown = new CountDownTimer(leftTime,10) {
			@Override
			public void onTick(long millisUntilFinished) {
				leftTime = millisUntilFinished;
				//if (leftTime <= 60000 && rang == false){
				if (leftTime <= 10000 && rang == false){ //デモ用
					rang = true;
					if (alermSwitch.isChecked()) {
						soundPool.play(lastOneMinSound, 1f, 1f, 0, 0, 1.0f);
					}
				}
				updateCountDownText();
			}

			@Override
			public void onFinish() {
				if (mode == 1) { //まだ質問に入ってなかったら(発表が終わったら)
					Log.i("デバッグ", "発表時間が終わりました。");
					if (alermSwitch.isChecked()) {soundPool.play(ptimeEndSound, 1f, 1f, 0, 0, 1.0f);}
					leftTime = qtime;
					updateCountDownText();
				} else { //質問も終わったら
					Log.i("デバッグ", "質問時間も終わりました");
					if (alermSwitch.isChecked()) { soundPool.play(qtimeEndSound, 1f, 1f, 0, 1, 1.0f);}
				}
				rang = false;
				flashTime = 5000;
				flash();
			}
		}.start();
	}



	private void pauseTimer(){  // 一時停止
		countDown.cancel();
		paused = true;
		run = false;
		finished = false;
	}

	private void resetTimer(){  // リセット
		if (run || paused) {
			if (mode == 1 || mode == 2){
				countDown.cancel();
			}
			if (mode == 3 || mode == 4){
				flashCountDown.cancel();
			}

			leftTime = ptime;
			updateCountDownText();
			finished = true;
			paused = false;
			mode = 1;
			run = false;
			radioEnabledTrue();
			timeProgressBar.setProgress(100);
			flashTime = 5000;
			start_pause.setBackgroundTintList(ColorStateList.valueOf(getResources().getColor(R.color.tcu)));
			timeProgressBar.setBackgroundTintList(ColorStateList.valueOf(getResources().getColor(R.color.tcu)));
			start_pause.setImageDrawable(getResources().getDrawable(R.drawable.ic_play)); //再生ボタンに変更
			pastText.setText("00:00.0");
		}
	}

	private void updateCountDownText(){ // 時刻の表示
		int minutes = (int)(leftTime/1000)/60;
		int seconds = (int)(leftTime/1000)%60;
		int ms = (int)((leftTime%1000)/100);

		int pastminutes;
		int pastseconds;
		int pastms;

		int timeprogress;

		if (mode == 1) { //はぴょう時間なら
			pastminutes = (int)((ptime-leftTime+100) / 1000)/60;
			pastseconds = (int)((ptime-leftTime) / 1000)%60;
			pastms = (int)((ptime-leftTime+100) %1000/100);
			timeprogress = ((int) (((float) leftTime / (float) ptime) * 100));
		} else {
			pastminutes = (int)((qtime-leftTime+100) / 1000)/60;
			pastseconds = (int)((qtime-leftTime) / 1000)%60;
			pastms = (int)((qtime-leftTime+100) %1000/100);
			timeprogress = ((int) (((float) leftTime / (float) qtime) * 100));
		}

		String timerLeftFormatted = String.format(java.util.Locale.JAPANESE, "%02d:%02d.%01d", minutes, seconds, ms);
		timerText.setText(timerLeftFormatted);
		String timerPastFormatted = String.format(java.util.Locale.JAPANESE, "%02d:%02d.%01d", pastminutes, pastseconds, pastms);
		pastText.setText(timerPastFormatted);
		timeProgressBar.setProgress(timeprogress);
	}

	public void flash () {
		if (mode == 1){
			mode = 3;
		} else if (mode == 2){
			mode = 4;
		}

		flashCountDown = new CountDownTimer(flashTime,500) {
			@Override
			public void onTick(long millisUntilFinished) {
				flashTime = millisUntilFinished;
				flashmode = !flashmode;
				flashparts();
				//Log.i("デバッグ", "点滅させています");
			}

			@Override
			public void onFinish() {
				flashTime = 5000;
				if (mode == 3) {
					mode = 2; //質問モードに
					start_pause.setBackgroundTintList(ColorStateList.valueOf(getResources().getColor(R.color.tokyu)));
					timeProgressBar.setBackgroundTintList(ColorStateList.valueOf(getResources().getColor(R.color.tokyu)));
					startTimer(); //質問時間スタート
					updateCountDownText();
				} else if (mode == 4) {
					resetTimer();
				}


			}
		}.start();
	}

	private void flashparts(){
		if (flashmode == true) {
			timerText.setText("00:00.0");
			//pastText.setText((((qtime)/1000)/60) + ":00.0");
			pastText.setText("00.20.0");//デモ用
			timeProgressBar.setProgress(0);
		} else {
			timerText.setText("");
			pastText.setText("");
			timeProgressBar.setProgress(100);
		}

	}

	private void radioEnabledFalse(){
		Log.i("デバッグ", "ラジオボタンを無効化します");
		for (int i = 0; i < ptime_radiobox.getChildCount(); i++) {
			ptime_radiobox.getChildAt(i).setEnabled(false);
		}
		for (int i = 0; i < qtime_radiobox.getChildCount(); i++) {
			qtime_radiobox.getChildAt(i).setEnabled(false);
		}

	}
	private void radioEnabledTrue(){
		Log.i("デバッグ", "ラジオボタンを有効化します");
		for (int i = 0; i < ptime_radiobox.getChildCount(); i++) {
			ptime_radiobox.getChildAt(i).setEnabled(true);
		}
		for (int i = 0; i < qtime_radiobox.getChildCount(); i++) {
			qtime_radiobox.getChildAt(i).setEnabled(true);
		}

	}

	public void radioSetTimeText10(View v){
		//timerText.setText("10:00.0");
		timerText.setText("00:20.0"); //デモ用
	}

	public void radioSetTimeText20(View v){
		timerText.setText("20:00.0");
	}

	public void radioSetTimeText30(View v){
		timerText.setText("30:00.0");
	}


	// 状態セーブ
	@Override
	protected void onSaveInstanceState(Bundle outState) {
		Log.i("デバッグ", "状態保存が呼ばれました: "+ ptime);
		super.onSaveInstanceState(outState);
		//boolean系
		outState.putBoolean("runstatus", run);
		outState.putBoolean("finishedstatus", finished);
		outState.putBoolean("pausedstatus", paused);
		outState.putBoolean("alermstatus", alerm);
		outState.putBoolean("rangstatus", rang);
		outState.putInt("modestatus", mode);


		//数値系
		outState.putLong("ptimestatus", ptime);
		outState.putLong("qtimestatus", qtime);
		outState.putLong("leftTimestatus", leftTime);
		outState.putLong("flashtimestatus", flashTime);
	}

}
