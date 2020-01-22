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
	システム開発演習B プロジェクト課題
	発表会支援タイマーアプリ
	動作確認機種: BlackBerry KEY2, HTL23
 */

public class MainActivity extends AppCompatActivity {

	boolean run = false; //タイマーが動作中かそうじゃないかを記録
	boolean finished = true; //一時停止状態か、終了/キャンセルで止まったのかを記録。
	boolean paused = false; //一時停止状態の記録
	int mode = 1; //1: 発表 2: 質問 3: 点滅用
	boolean flashmode = true;   //点滅の反転。本当はflashpartsに書くべきなのだが、回転対策のためここに記載。
	boolean alerm = true; //とりあえずデフォルトでTrueなので。これは状態復元時にスイッチの状態を維持するため
	boolean rang = false;   //残り1分の音が鳴ったか鳴らないか

	boolean recovered = false; // 再生成かどうかの判定

	private TextView timerText; //数字表示部のTextView
	private TextView pastText; //経過時間のTextView
	private ProgressBar timeProgressBar; //プログレスバー
	private FloatingActionButton start_pause;   //スタートストップボタン
	private FloatingActionButton cancel;    //キャンセルボタン
	private Switch alermSwitch; //音のオンオフスイッチ
	private CountDownTimer countDown; //カウントダウンクラス(ノーマルタイマー)
	private CountDownTimer flashCountDown; //点滅用タイマー
	private SimpleDateFormat dataFormat = new SimpleDateFormat("mm:ss.S", java.util.Locale.JAPANESE); //データフォーマット
	private RadioGroup ptime_radiobox;  //発表時間設定用ラジオボタン
	private RadioGroup qtime_radiobox;  //質問時間設定用ラジオボタン


	private SoundPool soundPool;
	private int lastOneMinSound;
	private int ptimeEndSound;
	private int qtimeEndSound;

	public long ptime; //発表時間設定を記録
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
		timerText.setText("10:00.0");
		//timerText.setText("00:20.0"); //デモ用
		pastText = findViewById(R.id.past_time);
		pastText.setText(dataFormat.format(0));

		//プログレスバー
		timeProgressBar = findViewById(R.id.progressBar);
		timeProgressBar.setProgress(100);

		//スイッチ
		alermSwitch = (Switch)findViewById(R.id.alerm_switch);

		//SoundPool
		final AudioAttributes audioAttributes = new AudioAttributes.Builder()
				.setUsage(AudioAttributes.USAGE_GAME)
				.setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
				.build();

		soundPool = new SoundPool.Builder()
				.setAudioAttributes(audioAttributes)
				.setMaxStreams(3)
				.build();

		// 音声ファイル準備
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

			/* UI系 */

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
					radioEnabledFalse(); //ラジオボタン無効化
				}

				if (finished) { //停止中だった場合
					recovered = false; //このあと特に必要ないのでfalse
					pastText.setText("00:00.0"); //除算の余りの関係で00:00.1になるので

					//停止時の文字復元
					if (ptime == 600000) {
					//if (ptime == 20000) { //デモ用
						timerText.setText("10:00.0");
						//timerText.setText("00:20.0"); //デモ用
					} else if (ptime == 1200000) {
						timerText.setText("20:00.0");
					} else if (ptime == 1800000) {
						timerText.setText("30:00.0");
					}
				}
			}


			/* ラジオボタン */
			// 発表時間
			if (ptime == 600000) {
			//if (ptime == 20000) { //デモ用
				ptime_radiobox.check(R.id.ptime10);
			} else if (ptime == 1200000) {
				ptime_radiobox.check(R.id.ptime20);
			} else if (ptime == 1800000) {
				ptime_radiobox.check(R.id.ptime30);
			}
			// 質問時間
			if (qtime == 300000) {
			//if (qtime == 15000) { //デモ用
				qtime_radiobox.check(R.id.qtime5);
			} else if (qtime == 600000) {
				qtime_radiobox.check(R.id.qtime10);
			}
		}


		/* 以下メイン処理 */

		// アラームスイッチの状態を一度確保
		alerm = alermSwitch.isChecked();

		// スタート・ストップボタンが押されたら
		start_pause.setOnClickListener(new View.OnClickListener(){
			@Override
			public void onClick(View spClick) {
				if (run) { //動作中なら
					if (mode == 1 || mode == 2){ //普通のタイマー状態だったら
						start_pause.setImageDrawable(getResources().getDrawable(R.drawable.ic_play)); //再生ボタンに変更
						pauseTimer(); //一時停止に移行
					} else if (mode == 3 || mode == 4){ //点滅中だったら
						Log.i("デバッグ","点滅中なので何もしませんよ");
					}
				} else {    // 動いてなかったら
					start_pause.setImageDrawable(getResources().getDrawable(R.drawable.ic_pause)); //一時停止ボタンに変更

					if (finished == true){ //完了後または初期状態の場合(これからタイマーを実行)

						// 発表時間設定の選択を取得
						int ptimeId = ptime_radiobox.getCheckedRadioButtonId();

						// ptimeに時間設定。(ms)
						if (ptimeId == R.id.ptime10) {
							ptime = 600000;  //10*60*1000 ms
							//ptime = 20000; //デモ用
						} else if (ptimeId == R.id.ptime20) {
							ptime = 1200000; //20*60*1000 ms
						} else if (ptimeId == R.id.ptime30) {
							ptime = 1800000; //30*60*1000 ms
						}


						// 質問時間設定の選択を取得
						int qtimeId = qtime_radiobox.getCheckedRadioButtonId();

						// qtimeに時間設定。(ms)
						if (qtimeId == R.id.qtime5) {
							qtime = 300000;  //5*60*1000 ms
							//qtime = 15000; //デモ用
						} else if (qtimeId == R.id.qtime10) {
							qtime = 600000;  //10*60*1000 ms
						}

					}

					radioEnabledFalse(); //ラジオボタン無効化
					startTimer(); // タイマースタート
				}
			}
		});

		// キャンセルボタンが押されたら
		cancel.setOnClickListener(new View.OnClickListener(){
			@Override
			public void onClick(View v){
				resetTimer(); //リセット叩くよ
			}
		});

	}

	private void startTimer() {  // タイマー実行

		run = true; //動作中

		if (paused == true){ //一時停止復帰
			paused = false;
			Log.i("デバッグ", "startTimerは一時停止復帰モードで起動しました");
		} else if (recovered){
			recovered = false;
			Log.i("デバッグ", "startTimerはActivity再生成モードで起動しました");
		} else { // ノーマル起動
			Log.i("デバッグ", "startTimerはノーマルモードで起動しました");
			if (mode == 1) {    //発表モードなら
				leftTime = ptime; //残り時間用変数に発表時間の値を代入
			} else if (mode == 2) { //質問モードなら
				leftTime = qtime; //残り時間用変数に質問時間の値を代入
			}
		}

		/* タイマー起動 */
		Log.i("デバッグ", "countDownTimerを起動します。leftTimeは "+ leftTime);

		countDown = new CountDownTimer(leftTime,10) {
			@Override
			public void onTick(long millisUntilFinished) {
				leftTime = millisUntilFinished;
				if (leftTime <= 60000 && rang == false){
				//if (leftTime <= 10000 && rang == false){ //デモ用
					rang = true; //音がなったか鳴らないかにかかわらずとりあえず鳴るタイミングになったので(複数回鳴ることを防止)

					if (alermSwitch.isChecked()) { //アラーム音スイッチがONなら
						soundPool.play(lastOneMinSound, 1f, 1f, 0, 0, 1.0f); //ﾋﾟﾋﾟﾋﾟﾋﾟ
					}
				}

				updateCountDownText(); //時間表示更新 (10ms毎に)
			}

			@Override
			public void onFinish() { //終わったら
				if (mode == 1) { //まだ質問に入ってなかったら(発表が終わったら)
					Log.i("デバッグ", "発表時間が終わりました。");

					//音を鳴らすぜ!
					if (alermSwitch.isChecked()) {soundPool.play(ptimeEndSound, 1f, 1f, 0, 0, 1.0f);}

					leftTime = qtime;   //次のために質問時間用の時間をセット

					updateCountDownText();  //おまじない

				} else { //質問も終わったら
					Log.i("デバッグ", "質問時間も終わりました");

					//音を鳴らすぜ!
					if (alermSwitch.isChecked()) { soundPool.play(qtimeEndSound, 1f, 1f, 0, 1, 1.0f);}
				}

				rang = false; //複数回鳴るのを防止
				flashTime = 5000; //5秒間点滅してもらいます
				flash();    //それでは点滅どうぞー
			}
		}.start();
	}


	//一時停止
	private void pauseTimer(){
		countDown.cancel(); //一旦キャンセルします
		paused = true; //一時停止状態に
		run = false;    //動作はしてない
		finished = false;   //終了もしてない
	}

	//リセット
	private void resetTimer(){
		if (run || paused) { //動作中か一時停止中のみ効きます(ぬるぽ防止)
			if (mode == 1 || mode == 2){ //普通のタイマーだったら
				countDown.cancel(); //キャンセル
			} else  if (mode == 3 || mode == 4){ //点滅モードだったら
				flashCountDown.cancel();    //点滅モードもキャンセル
			}

			leftTime = ptime;   //残り時間用変数をptimeに戻す
			updateCountDownText();  //表記をリセット
			finished = true;    //終了状態
			paused = false;     //一時停止状態ではない
			mode = 1;           //初期状態に
			run = false;        //動作中じゃない
			radioEnabledTrue(); //ラジオボタンを有効化
			timeProgressBar.setProgress(100);   //円を白くする
			flashTime = 5000;      //点滅が途中キャンセルされてる可能性もあるのでね･･･おまじない的な

			//ボタンとバーの色を青に戻す
			start_pause.setBackgroundTintList(ColorStateList.valueOf(getResources().getColor(R.color.tcu)));
			timeProgressBar.setBackgroundTintList(ColorStateList.valueOf(getResources().getColor(R.color.tcu)));

			start_pause.setImageDrawable(getResources().getDrawable(R.drawable.ic_play)); //再生ボタンに変更

			pastText.setText("00:00.0");    //文字を0に戻す(言語関係ないのでセーフ)
		}
	}

	// 時刻の表示
	private void updateCountDownText(){

		//残り時間
		int minutes = (int)(leftTime/1000)/60;  //残り時間から分を計算
		int seconds = (int)(leftTime/1000)%60;  //残り時間から病を計算
		int ms = (int)((leftTime%1000)/100);    //残り時間から1/10秒を計算

		//経過時間(モードによって変わるので内容は後述)
		int pastminutes;    //分
		int pastseconds;    //秒
		int pastms;         //ms

		int timeprogress;   //プログレスバーの割合(0~100)

		//経過時間の処理とプログレスバーの割合計算
		if (mode == 1) {    //発表時間なら
			pastminutes = (int)((ptime-leftTime+100) / 1000)/60;                //分
			pastseconds = (int)((ptime-leftTime) / 1000)%60;                    //秒
			pastms = (int)((ptime-leftTime+100) %1000/100);                     //ms
			timeprogress = ((int) (((float) leftTime / (float) ptime) * 100));  //割合(%)
		} else {
			pastminutes = (int)((qtime-leftTime+100) / 1000)/60;                //分
			pastseconds = (int)((qtime-leftTime) / 1000)%60;                    //秒
			pastms = (int)((qtime-leftTime+100) %1000/100);                     //ms
			timeprogress = ((int) (((float) leftTime / (float) qtime) * 100));  //割合(%)
		}

		//フォーマット指定
		String timerLeftFormatted = String.format(java.util.Locale.JAPANESE, "%02d:%02d.%01d", minutes, seconds, ms); //残り
		String timerPastFormatted = String.format(java.util.Locale.JAPANESE, "%02d:%02d.%01d", pastminutes, pastseconds, pastms); //経過

		timerText.setText(timerLeftFormatted); //残り時間表示
		pastText.setText(timerPastFormatted);   //経過時間表示
		timeProgressBar.setProgress(timeprogress);  //プログレスバー更新
	}

	//点滅
	public void flash () {
		if (mode == 1){ //発表モードなら
			mode = 3; //発表用点滅モードに
		} else if (mode == 2){  //質問モードなら
			mode = 4;           //質問用点滅モードに
		}

		//点滅用にタイマーを作ります(5秒間(またはActivity再生成後の残り時間)、500msごとに更新。)
		flashCountDown = new CountDownTimer(flashTime,500) {
			//500ms毎に
			@Override
			public void onTick(long millisUntilFinished) {
				flashTime = millisUntilFinished;    //残り時間を記録
				flashmode = !flashmode;             //点滅するので入れ替える(flashparsで使う)
				flashparts();                       //↑のフラグに従って表示or非表示を切り替える
			}

			//終わったら
			@Override
			public void onFinish() {
				flashTime = 5000;   //一旦元に戻します
				if (mode == 3) {    //発表用点滅モードだったら
					mode = 2; //質問モードに

					//色を赤に
					start_pause.setBackgroundTintList(ColorStateList.valueOf(getResources().getColor(R.color.tokyu)));
					timeProgressBar.setBackgroundTintList(ColorStateList.valueOf(getResources().getColor(R.color.tokyu)));

					startTimer(); //質問時間スタート
					updateCountDownText(); //おまじない
				} else if (mode == 4) { //質問用点滅モード
					resetTimer();   //全部終わったのでリセット
				}

			}
		}.start();
	}

	//点滅させるよ!(表示と非表示入れ替えるよ!)
	private void flashparts(){
		//flashmodeは500ms毎に入れ替わってから叩かれるので点滅に見えます

		if (flashmode == true) {
			timerText.setText("00:00.0");   //残り0だからね。
			pastText.setText((((qtime)/1000)/60) + ":00.0");  //本来はこれやりますがデモは1分未満なので
			//pastText.setText("00.20.0");//デモ用
			timeProgressBar.setProgress(0); //円が全部青か赤に
		} else {
			timerText.setText("");  //文字が虚無です
			pastText.setText("");   //こちらも虚無にする
			timeProgressBar.setProgress(100);   //円を白くする
		}

	}

	//ラジオボタンを無効化
	private void radioEnabledFalse(){
		for (int i = 0; i < ptime_radiobox.getChildCount(); i++) {
			ptime_radiobox.getChildAt(i).setEnabled(false);
		}
		for (int i = 0; i < qtime_radiobox.getChildCount(); i++) {
			qtime_radiobox.getChildAt(i).setEnabled(false);
		}

	}

	//ラジオボタンを有効化
	private void radioEnabledTrue(){
		Log.i("デバッグ", "ラジオボタンを有効化します");
		for (int i = 0; i < ptime_radiobox.getChildCount(); i++) {
			ptime_radiobox.getChildAt(i).setEnabled(true);
		}
		for (int i = 0; i < qtime_radiobox.getChildCount(); i++) {
			qtime_radiobox.getChildAt(i).setEnabled(true);
		}

	}


	/* 以下3つは発表時間をラジオボタンで切り替えてるときに表示部も変えるため */
	public void radioSetTimeText10(View v){
		timerText.setText("10:00.0");
		//timerText.setText("00:20.0"); //デモ用
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