package com.example.contentdownloader;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;

import android.app.Activity;
import android.app.AlarmManager;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.os.AsyncTask;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.os.PowerManager;
import android.os.SystemClock;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

public class MainActivity extends Activity {

	// declare the dialog as a member field of your activity
	ProgressBar mProgressBar;
	TextView mPrcntText;
	DownloadTask downloadTask;

	boolean mIsDownloading;
	long mDownloaded;
	long mFileLength;

	Button cancelBtn;
	Button startBtn;

	BroadcastReceiver mScheduleReceiver;
	PendingIntent mPendingIntent;
	AlarmManager mAlarmManager;

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.activity_main);

		// instantiate it within the onCreate method
		mProgressBar = (ProgressBar) findViewById(R.id.progressBar);
		mProgressBar.setIndeterminate(true);

		cancelBtn = (Button) findViewById(R.id.button1);
		mPrcntText = (TextView) findViewById(R.id.textView1);
		mPrcntText.setText("0%");

		startBtn = (Button) findViewById(R.id.button2);

		cancelBtn.setEnabled(false);

		mDownloaded = 0;

		mScheduleReceiver = new BroadcastReceiver() {

			@Override
			public void onReceive(Context context, Intent intent) {
				final Handler h = new Handler();
				h.postDelayed(new Runnable() {

					@Override
					public void run() {
						if (isOnline()) {
							downloadTask = new DownloadTask(MainActivity.this);
							downloadTask.execute("ENTER_URL_HERE");		
							Log.e("hiemanshu", "Starting Download");
							cancelBtn.setEnabled(true);
							startBtn.setEnabled(false);
						} else {
							Toast.makeText(getApplicationContext(), "No wifi connected, trying again in 30 seconds", Toast.LENGTH_LONG).show();
							h.postDelayed(this, 30*1000);
						}
					}
				}, 0);
			}
		};

		registerReceiver(mScheduleReceiver, new IntentFilter(getPackageName()));
		mPendingIntent = PendingIntent.getBroadcast(this, 0, new Intent(getPackageName()), 0);
		mAlarmManager = (AlarmManager) getSystemService(Context.ALARM_SERVICE);

		startBtn.setOnClickListener(new View.OnClickListener() {

			@Override
			public void onClick(View v) {  
				mAlarmManager.set(AlarmManager.ELAPSED_REALTIME_WAKEUP, SystemClock.elapsedRealtime() + 20000, mPendingIntent);
			}
		});


		cancelBtn.setOnClickListener(new View.OnClickListener() {

			@Override
			public void onClick(View v) {
				downloadTask.cancel(true);
				cancelBtn.setEnabled(false);
				startBtn.setEnabled(true);
			}
		});

	}

	@Override
	protected void onDestroy() {
		mAlarmManager.cancel(mPendingIntent);
		unregisterReceiver(mScheduleReceiver);
		super.onDestroy();
	}

	public boolean isOnline() {
		ConnectivityManager cm = (ConnectivityManager) getSystemService(Context.CONNECTIVITY_SERVICE);
		NetworkInfo netInfo = cm.getActiveNetworkInfo();
		if (netInfo != null && netInfo.isConnectedOrConnecting()) {
			return true;
		}
		return false;
	}

	private class DownloadTask extends AsyncTask<String, Integer, String> {

		private Context context;

		public DownloadTask(Context context) {
			this.context = context;
		}

		@Override
		protected String doInBackground(String... sUrl) {
			// take CPU lock to prevent CPU from going off if the user 
			// presses the power button during download
			PowerManager pm = (PowerManager) context.getSystemService(Context.POWER_SERVICE);
			PowerManager.WakeLock wl = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK,
					getClass().getName());
			wl.acquire();

			try {
				InputStream input = null;
				OutputStream output = null;
				HttpURLConnection connection = null;
				try {
					URL url = new URL(sUrl[0]);
					connection = (HttpURLConnection) url.openConnection();
					if (mIsDownloading) {
						connection.setRequestProperty("Range", "bytes=" + mDownloaded + "-");
						File f = new File(Environment.getExternalStorageDirectory() + "/download.mp4");
						Log.e("hiemanshu", "File length is " + f.length());
						Log.e("hiemanshu", "Set range to " + mDownloaded);
					}
					connection.connect();

					// expect HTTP 200 OK, so we don't mistakenly save error report 
					// instead of the file
					if (connection.getResponseCode() != HttpURLConnection.HTTP_OK)
						Log.e("hiemanshu", "Server returned HTTP " + connection.getResponseCode() + " " + connection.getResponseMessage());

					// this will be useful to display download percentage
					// might be -1: server did not report the length
					long fileLength = - 1;
					if (mIsDownloading) {
						fileLength = mDownloaded + connection.getContentLength();
					} else {
						fileLength = connection.getContentLength();
					}

					mFileLength = fileLength;

					Log.e("hiemanshu", "File length is " + fileLength);

					// download the file
					input = connection.getInputStream();
					if (mIsDownloading)
						output = new FileOutputStream(Environment.getExternalStorageDirectory() + "/download.mp4", true);
					else
						output = new FileOutputStream(Environment.getExternalStorageDirectory() + "/download.mp4");

					byte data[] = new byte[4096];
					long total = 0;
					int count;
					while ((count = input.read(data)) != -1) {
						// allow canceling with back button
						if (isCancelled())
							return null;
						mIsDownloading = true;
						total += count;
						mDownloaded = mDownloaded + count;
						// publishing the progress....
						if (fileLength > 0) // only if total length is known
							publishProgress((int) (mDownloaded * 100 / fileLength));
						output.write(data, 0, count);
						Log.e("hiemanshu", "Downloaded so far : " + mDownloaded);                        
					}
				} catch (Exception e) {
					return e.toString();
				} finally {
					try {
						if (output != null)
							output.close();
						if (input != null)
							input.close();
					} 
					catch (IOException ignored) { }

					if (connection != null)
						connection.disconnect();
				}
			} finally {
				wl.release();
			}
			return null;
		}

		@Override
		protected void onPreExecute() {
			super.onPreExecute();
		}

		@Override
		protected void onProgressUpdate(Integer... progress) {
			super.onProgressUpdate(progress);
			// if we get here, length is known, now set indeterminate to false
			mProgressBar.setIndeterminate(false);
			mProgressBar.setMax(100);
			mProgressBar.setProgress(progress[0]);
			mPrcntText.setText(progress[0] + "%");
		}

		@Override
		protected void onPostExecute(String result) {
			if (result != null)
				Toast.makeText(context,"Download error: "+result, Toast.LENGTH_LONG).show();
			else
				Toast.makeText(context,"File downloaded", Toast.LENGTH_SHORT).show();

			if (mFileLength == mDownloaded) {
				mIsDownloading = false;
				mDownloaded = 0;
			} else {
				Log.e("hiemanshu", "Download not complete, trying again in 30 seconds");
				mAlarmManager.set(AlarmManager.ELAPSED_REALTIME_WAKEUP, SystemClock.elapsedRealtime() + 30000, mPendingIntent);
			}
			cancelBtn.setEnabled(false);
			startBtn.setEnabled(true);
		}
	}
}
