/*///////////////////////////////////////////////////////////////// 
                          _ooOoo_                               
                         o8888888o                              
                         88" . "88                              
                         (| ^_^ |)                              
                         O\  =  /O                              
                      ____/`---'\____                            
                    .'  \\|     |//  `.                          
                   /  \\|||  :  |||//  \                        
                  /  _||||| -:- |||||-  \                       
                  |   | \\\  -  /// |   |                       
                  | \_|  ''\---/''  |   |                       
                  \  .-\__  `-`  ___/-. /                        
                ___`. .'  /--.--\  `. . ___                      
              ."" '<  `.___\_<|>_/___.'  >'"".                
            | | :  `- \`.;`\ _ /`;.`/ - ` : | |                  
            \  \ `-.   \_ __\ /__ _/   .-` /  /                 
      ========`-.____`-.___\_____/___.-`____.-'========          
                           `=---='                               
      ^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^        
                     佛祖保佑    永无BUG                         
                   Code by duxu0711@163.com                      
////////////////////////////////////////////////////////////////*/

package cn.geekduxu.xmanager;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.ProtocolException;
import java.net.URL;
import java.nio.ByteBuffer;

import net.tsz.afinal.FinalHttp;
import net.tsz.afinal.http.AjaxCallBack;

import org.json.JSONException;
import org.json.JSONObject;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.AlertDialog.Builder;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.content.pm.PackageManager.NameNotFoundException;
import android.database.ContentObserver;
import android.net.Uri;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.os.Message;
import android.os.SystemClock;
import android.view.animation.AlphaAnimation;
import android.widget.TextView;
import android.widget.Toast;
import cn.geekduxu.xmanager.activity.HomeActivity;
import cn.geekduxu.xmanager.activity.SettingActivity;
import cn.geekduxu.xmanager.receiver.SmsReceiver2;
import cn.geekduxu.xmanager.service.AddressService;
import cn.geekduxu.xmanager.utils.StreamTools;

public class SplashActivity extends Activity {

	/** 进入主页消息 */
	private static final int ENTER_HOME_PAGE = 0;
	/** 显示升级对话框消息 */
	private static final int SHOW_UPDATE_DIALOG = 1;
	/** URL错误消息 */
	private static final int URL_ERROR = 2;
	/** 网络错误消息 */
	private static final int NETWORK_ERROR = 3;
	/** JSON解析错误消息 */
	private static final int JSON_ERROR = 4;

	private SharedPreferences sp;
	private TextView tvSplashVersion;
	private TextView tvUpdateInfo;

	/** 新版本的描述信息 */
	private String description;
	/** 新版本版本号 */
	private String version;
	/** 新版本的下载地址 */
	private String apkurl;

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.activity_splash);

		sp = getSharedPreferences("config", MODE_PRIVATE);

		// 增加动画效果
		AlphaAnimation aa = new AlphaAnimation(0.2f, 1.0f);
		aa.setDuration(1000);
		findViewById(R.id.rl_splash).startAnimation(aa);

		// 得到实例并设置文字
		tvSplashVersion = (TextView) findViewById(R.id.tv_splash_version);
		tvSplashVersion.setText("版本号：" + getVersionName());

		tvUpdateInfo = (TextView) findViewById(R.id.tv_splash_updateinfo);

		// 开启服务
		startServices();

		// 拷贝数据库文件到包下。
		new Thread() {
			public void run() {
				copyDatabase();
			}
		}.start();

		// 得到SharedPreferences中是否自动升级字段
		if (sp.getBoolean("update", false)) {
			// 检查升级
			checkUpdate();
		} else {
			// 延迟进入主页
			handler.postDelayed(new Runnable() {
				@Override
				public void run() {
					enterHomePage();
				}
			}, 2500);
		}
	}

	private void startServices() {
		if (sp.getBoolean("showaddress", true)) {
			Intent intent = new Intent(SplashActivity.this,
					AddressService.class);
			startService(intent);
		}
		// ***************************************************
		ContentObserver observer = new SmsReceiver2(new Handler(),
				getApplicationContext());
		this.getContentResolver().registerContentObserver(
				Uri.parse("content://sms/"), true, observer);
		// ***************************************************

	}

	/**
	 * 拷贝归属地数据库文件到包目录下
	 */
	private void copyDatabase() {
		try {
			File dbFile = new File(getFilesDir(), "address.db");
			if (dbFile.exists() && dbFile.length() > 0) {
				return;
			}
			InputStream is = getAssets().open("address.db");
			FileOutputStream fos = new FileOutputStream(dbFile);
			byte[] buffer = new byte[1024];
			int length = 0;

			while ((length = is.read(buffer)) != -1) {
				fos.write(buffer, 0, length);
				fos.flush();
			}

			fos.close();
			is.close();

		} catch (IOException e) {
		}
	}

	private Handler handler = new Handler() {
		@Override
		public void handleMessage(android.os.Message msg) {
			switch (msg.what) {
			case SHOW_UPDATE_DIALOG: // 显示升级对话框
				showUpdateDialog();
				break;
			case ENTER_HOME_PAGE: // 进入主页面
				enterHomePage();
				break;
			case URL_ERROR: // URL错误
				Toast.makeText(SplashActivity.this, "URL出错", 0).show();
				enterHomePage();
				break;
			case NETWORK_ERROR: // 网络异常
				Toast.makeText(SplashActivity.this, "网络出错", 0).show();
				enterHomePage();
				break;
			case JSON_ERROR: // json解析出错
				Toast.makeText(SplashActivity.this, "JSON出错", 0).show();
				enterHomePage();
				break;
			}
		}

	};

	/**
	 * 进入主页
	 */
	private void enterHomePage() {
		Intent intent = new Intent(SplashActivity.this, HomeActivity.class);
		startActivity(intent);
		// 关闭当前页面
		finish();
		overridePendingTransition(android.R.anim.fade_out,
				android.R.anim.fade_in);
	}

	/**
	 * 显示升级对话框
	 */
	private void showUpdateDialog() {
		AlertDialog.Builder builder = new Builder(SplashActivity.this);
		builder.setTitle("升级提示");
		builder.setMessage(description);
		builder.setPositiveButton("立刻升级", positiveButtonListener);
		// builder.setCancelable(false);
		builder.setOnCancelListener(new DialogInterface.OnCancelListener() {
			@Override
			public void onCancel(DialogInterface dialog) {
				dialog.dismiss();
				enterHomePage();
			}
		});
		builder.setNegativeButton("暂不升级",
				new DialogInterface.OnClickListener() {
					@Override
					public void onClick(DialogInterface dialog, int which) {
						dialog.dismiss();
						enterHomePage();
					}
				});
		builder.show();
	}

	/**
	 * 检车是否存在新的版本
	 */
	private void checkUpdate() {
		new Thread(checkNewVersionRunnable).start();
	}

	/**
	 * 检查新版本线程
	 */
	private Runnable checkNewVersionRunnable = new Runnable() {
		@Override
		public void run() {
			Message msg = Message.obtain();
			long time = System.currentTimeMillis();
			try {
				URL url = new URL(getString(R.string.server_url));
				// 联网
				HttpURLConnection conn = (HttpURLConnection) url.openConnection();
				conn.setRequestMethod("GET");
				conn.setReadTimeout(3000);//3秒超时
				int code = conn.getResponseCode();
				// 如果请求成功
				if (code / 100 != 2) {
					return;
				} 
				//else
				InputStream is = conn.getInputStream();
				// 把结果转化为字符串
				String result = StreamTools.readFromStream(is);
				// 解析json
				JSONObject obj = new JSONObject(result);
				version = obj.getString("version");
				description = obj.getString("description");
				apkurl = obj.getString("apkurl");
				// 检验是否存在新版本
				if (getVersionName().equals(version)) {
					// 没有新版本
					msg.what = ENTER_HOME_PAGE;
				} else {
					// 存在新的版本
					msg.what = SHOW_UPDATE_DIALOG;
				}
			} catch (MalformedURLException e) {
				msg.what = URL_ERROR;
			} catch (IOException e) {
				msg.what = NETWORK_ERROR;
			} catch (JSONException e) {
				msg.what = JSON_ERROR;
			} finally {
				time = System.currentTimeMillis() - time;
				if (time < 2500) {
					SystemClock.sleep(2500 - time);
				}
				handler.sendMessage(msg);
			}

		}
	};

	/**
	 * 得到当前的版本号
	 */
	private String getVersionName() {
		try {
			// PackageManager可以用来管理APK
			PackageManager pm = getPackageManager();
			// 得到本程序的清单文件信息
			PackageInfo info = pm.getPackageInfo(getPackageName(), 0);
			return info.versionName;
		} catch (NameNotFoundException e) {
			return null;
		}
	}

	/**
	 * 是否升级对话框<b>立即升级</b>按钮的监听器
	 */
	private DialogInterface.OnClickListener positiveButtonListener = new DialogInterface.OnClickListener() {
		@Override
		public void onClick(DialogInterface dialog, int which) {
			// 下载apk并且替换安装
			if (Environment.getExternalStorageState().equals(
					Environment.MEDIA_MOUNTED)) {
				// 存在sd卡
				FinalHttp finalHttp = new FinalHttp();
				finalHttp.download(apkurl, Environment
						.getExternalStorageDirectory().getAbsolutePath()
						+ "xManager-" + version + ".apk", ajaxCallback);
			} else {
				// 不存在sd卡
				Toast.makeText(SplashActivity.this, "没有找到SD卡，请先安装SD卡。",
						Toast.LENGTH_LONG).show();
				return;
			}
		}
	};

	/**
	 * 下载响应
	 */
	private AjaxCallBack<File> ajaxCallback = new AjaxCallBack<File>() {
		@Override
		public void onFailure(Throwable t, int errorNo, String strMsg) {
			Toast.makeText(SplashActivity.this, "下载更新失败，请稍后再试。",
					Toast.LENGTH_LONG).show();
			super.onFailure(t, errorNo, strMsg);
			enterHomePage();
		}

		@Override
		public void onLoading(long count, long current) {
			super.onLoading(count, current);
			// 显示当前下载的百分比
			tvUpdateInfo.setText("下载进度：" + (current * 100 / count) + "%");
		}

		@Override
		public void onSuccess(File file) {
			super.onSuccess(file);
			// 下载成功，安装apk
			installApk(file);
		}

		/**
		 * 安装apk
		 * 
		 * @param file
		 */
		private void installApk(File file) {
			Intent intent = new Intent();
			intent.setAction("android.intent.action.VIEW");
			intent.addCategory("android.intent.category.DEFAULT");
			intent.setDataAndType(Uri.fromFile(file),
					"application/vnd.android.package-archive");
			startActivity(intent);
		}
	};
}
