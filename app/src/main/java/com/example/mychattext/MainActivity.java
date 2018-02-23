package com.example.mychattext;

import android.Manifest;
import android.app.Activity;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.support.v4.app.ActivityCompat;
import android.util.Log;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.Window;
import android.widget.Button;
import android.widget.ListView;
import android.widget.Toast;

import com.alibaba.fastjson.JSON;
import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import com.iflytek.cloud.ErrorCode;
import com.iflytek.cloud.InitListener;
import com.iflytek.cloud.RecognizerResult;
import com.iflytek.cloud.SpeechConstant;
import com.iflytek.cloud.SpeechError;
import com.iflytek.cloud.SpeechUtility;
import com.iflytek.cloud.ui.RecognizerDialog;
import com.iflytek.cloud.ui.RecognizerDialogListener;

import org.apache.http.HttpResponse;
import org.apache.http.HttpStatus;
import org.apache.http.client.HttpClient;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.impl.client.DefaultHttpClient;
import org.apache.http.util.EntityUtils;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import entity.AppBean;
import entity.PersonChat;
import utils.CommonUtil;

public class MainActivity extends Activity {
	private ChatAdapter chatAdapter;
	private String execAppReg = "^(执行|运行|打开|启动)(.+)";//启动其他应用正则
	private String callPhoneReg = "^((13[0-9])|(14[5|7])|(15([0-3]|[5-9]))|(18[0,5-9]))\\d{8}$";//电话号正则

	private volatile static Map<String, AppBean> appInfoMap = new ConcurrentHashMap<>();
	private boolean initialized = false;

	/**
	 * 声明ListView
	 */
	private ListView lv_chat_dialog;

	private Map<String, Object> msgMap;

	/**
	 * 集合
	 */
	private List<PersonChat> personChats = new ArrayList<PersonChat>();
	private Handler handler = new Handler() {
		public void handleMessage(android.os.Message msg) {
			int what = msg.what;
			switch (what) {
				case 1:
					/**
					 * ListView条目控制在最后一行
					 */
					lv_chat_dialog.setSelection(personChats.size());
					break;

				default:
					break;
			}
		}

		;
	};

	public static final String TAG = "MainActivity";
	private InitListener mInitListener = new InitListener() {
		@Override
		public void onInit(int code) {
			Log.d(TAG, "SpeechRecognizer init() code = " + code);
			if (code != ErrorCode.SUCCESS) {
				Toast.makeText(MainActivity.this, "初始化失败，错误码：" + code, Toast.LENGTH_SHORT).show();
			}
		}
	};

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		requestWindowFeature(Window.FEATURE_NO_TITLE);
		setContentView(R.layout.activity_main);

		getAllAppInfo();//获取系统所有已安装应用信息
		PersonChat robotChat = new PersonChat();
		robotChat.setMeSend(false);
		robotChat.setChatMessage(getString(R.string.hello));
		personChats.add(robotChat);
		lv_chat_dialog = (ListView) findViewById(R.id.lv_chat_dialog);
		Button btn_chat_message_send = (Button) findViewById(R.id.btn_chat_message_send);
//		final EditText et_chat_message = (EditText) findViewById(R.id.et_chat_message);
		chatAdapter = new ChatAdapter(this, personChats);
		lv_chat_dialog.setAdapter(chatAdapter);

		btn_chat_message_send.setOnClickListener(btnSendListener);
	}

	private View.OnClickListener btnSendListener = new OnClickListener() {

		@Override
		public void onClick(View arg0) {
			//有动画效果
			RecognizerDialog iatDialog;
			// ①语音配置对象初始化
			SpeechUtility.createUtility(MainActivity.this, SpeechConstant.APPID + "=" + getString(R.string.xf_key));
			// ②初始化有交互动画的语音识别器
			iatDialog = new RecognizerDialog(MainActivity.this, mInitListener);
			//③设置监听，实现听写结果的回调
			iatDialog.setListener(new RecognizerDialogListener() {
			String resultJson = "[";//放置在外边做类的变量则报错，会造成json格式不对（？）

			@Override
			public void onResult(RecognizerResult recognizerResult, boolean isLast) {
				System.out.println("-----------------   onResult   -----------------");
				if (!isLast) {
					resultJson += recognizerResult.getResultString() + ",";
				} else {
					resultJson += recognizerResult.getResultString() + "]";
				}
				if (isLast) {
					//解析语音识别后返回的json格式的结果
					Gson gson = new Gson();
					List<DictationResult> resultList = gson.fromJson(resultJson,
							new TypeToken<List<DictationResult>>() {
							}.getType());
					String result = "";
					for (int i = 0; i < resultList.size() - 1; i++) {
						result += resultList.get(i).toString();
					}
					PersonChat personChat = new PersonChat();
					//代表自己发送
					personChat.setMeSend(true);
					//得到发送内容
					personChat.setChatMessage(result);
					//加入集合
					personChats.add(personChat);
					//清空输入框
//							et_chat_message.setText("");

					//拨打电话
					Pattern callPatt= Pattern.compile(callPhoneReg);
					Matcher callMat=callPatt.matcher(result);
					if (callMat.find()){
						Intent intent = new Intent(Intent.ACTION_CALL, Uri.parse("tel:" + result));
						if (ActivityCompat.checkSelfPermission(MainActivity.this, Manifest.permission.CALL_PHONE) == PackageManager.PERMISSION_GRANTED) {
							startActivity(intent);
							return;
						}
					}

					//打开应用
					Pattern appPatt= Pattern.compile(execAppReg);
					Matcher appMat=appPatt.matcher(result);
					while (appMat.find()){//是打开应用指令
						if(!initialized){
							Toast.makeText(MainActivity.this,getString(R.string.sys_busy),Toast.LENGTH_SHORT).show();
							return;
						}
						String voiceName = appMat.group(2).toLowerCase();//语音所说名称(统一小写)
						if(appInfoMap.containsKey(voiceName)){
							Intent resolveIntent = getPackageManager().getLaunchIntentForPackage(appInfoMap.get(voiceName).getAppPackageName());
							startActivity(resolveIntent);// 启动目标应用
							return;
						}
					}

					sendToRobotApi(result);//向机器人api发请求

					//刷新ListView
					chatAdapter.notifyDataSetChanged();
					handler.sendEmptyMessage(1);
				}
			}

			@Override
			public void onError(SpeechError speechError) {
				//自动生成的方法存根
				speechError.getPlainDescription(true);
			}
		});
		//开始听写，需将sdk中的assets文件下的文件夹拷入项目的assets文件夹下（没有的话自己新建）
		iatDialog.show();
		}
	};

	private void sendToRobotApi(final String finalResult){
		new Thread(new Runnable() {
			@Override
			public void run() {
				PersonChat robotChat = new PersonChat();
				robotChat.setMeSend(false);
				String url = String.format("https://way.jd.com/turing/turing?info=%s&loc=北京市&userid=222&appkey=%s", finalResult,
						getString(R.string.app_key));
				Map<String, Object> msgMap = getMessageFromApi(url);
				if("10000".equals(msgMap.get("code"))){
					String answer = ((Map<String,String>)msgMap.get("result")).get("text");
					robotChat.setChatMessage(answer);
				}else{
					robotChat.setChatMessage(getString(R.string.sys_busy));
				}
				personChats.add(robotChat);
				handler.post(new Runnable() {
					@Override
					public void run() {
						//刷新ListView
						chatAdapter.notifyDataSetChanged();
						handler.sendEmptyMessage(1);
					}
				});
			}
		}).start();
	}


	private Map<String,Object> getMessageFromApi(final String url){
		Thread getThread = new Thread(new Runnable() {
			@Override
			public void run() {
				// 生成请求对象
				HttpGet httpGet = new HttpGet(url);
				HttpClient httpClient = new DefaultHttpClient();

				// 发送请求
				try{
					HttpResponse response = httpClient.execute(httpGet);
					if (response.getStatusLine().getStatusCode() == HttpStatus.SC_OK) {
						String msg = EntityUtils.toString(response.getEntity());
						msgMap = (Map)JSON.parse(msg);
					}
				}
				catch (Exception e){
					e.printStackTrace();
				}
			}
		});
		getThread.start();
		try {
			getThread.join();
		} catch (InterruptedException e) {
			return null;
		}
		return msgMap;
	}

	private void getAllAppInfo(){
		new Thread(new Runnable() {
			@Override
			public void run() {
				List<AppBean> allApk = CommonUtil.getAllApk();
				for(AppBean app:allApk){
					appInfoMap.put(app.getAppName().toLowerCase(),app);//统一小写
				}
				initialized = true;
			}
		}).start();
	}

}
