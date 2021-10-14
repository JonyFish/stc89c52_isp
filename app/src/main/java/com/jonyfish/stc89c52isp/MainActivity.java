package com.jonyfish.stc89c52isp;

import android.content.*;
import android.os.*;
import android.view.*;
import android.widget.*;

import android.app.Activity;
import android.hardware.usb.UsbManager;
import android.net.Uri;
import android.provider.Settings;
import android.view.View.OnClickListener;
import cn.wch.ch34xuartdriver.CH34xUARTDriver;
import java.nio.ByteBuffer;
import java.util.Arrays;

public class MainActivity extends Activity {

	private TextView readText;
	private Button downloadButton;
	private EditText pathEditText;
	private ScrollView scrollView;

	private Handler handler;


	/* by default it is 9600 */
	public int baudRate=2400;
	/* default is stop bit 1 */
	public byte stopBit=1;
	/* default data bit is 8 bit */
	public byte dataBit=8;
	/* default is none */
	public byte parity=0;
	/* default flow control is is none */
	public byte flowControl=0;


	private static final String ACTION_USB_PERMISSION = "com.mycompany.myapp.USB_PERMISSION";

	private static final byte CMD_CHECK_BAUD=(byte) 0x8F;//检测波特率
	private static final byte CMD_CONFIRM_BAUD=(byte) 0x8E;//配置波特率
	private static final byte CMD_ACK=(byte) 0x80;//握手
	private static final byte CMD_ERASE=(byte) 0x84;//擦除

	private static final byte[] baudrate_chk_11M_2400 = {(byte)0xFF,(byte)0x71,(byte) 0x00,(byte) 0xFE,(byte) 0x28,(byte) 0x82};//检测波特率数据包
	private static final byte[] baudrate_chg_11M_2400 = {(byte)0xFF,(byte)0x71,(byte) 0x00,(byte) 0xFE,(byte) 0x28};//修改波特率数据包

	private static final byte[] ack = {(byte)0x00,(byte)0x00,(byte) 0x36,(byte) 0x01,(byte) 0xF0,(byte)0x02};//握手数据包



	private static int REQUEST_PERMISSION_CODE=1;

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);

		requestWindowFeature(Window.FEATURE_NO_TITLE);
		setContentView(R.layout.main);

		initUI();


		if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
			// 适配android11读写权限
			if (Environment.isExternalStorageManager()) {
				//已获取android读写权限
			} else {
				Intent intent = new Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION);
				intent.setData(Uri.parse("package:" + getPackageName()));
				startActivityForResult(intent, REQUEST_PERMISSION_CODE);
			}
		}



	}



	// 处理界面
	private void initUI() {

		readText = findViewById(R.id.readTextView);
		downloadButton = findViewById(R.id.downloadButton);
		pathEditText = findViewById(R.id.mainEditText1);
		scrollView = findViewById(R.id.mainScrollView1);

		downloadButton.setOnClickListener(new DownloadButtonOnClickListener());

		handler = new Handler() {
			public void handleMessage(Message msg) {
				readText.append("\n" + msg.obj);
				scrollView.fullScroll(ScrollView.FOCUS_UP);//滚动到顶部
			}
		};

	}

	//界面输出日志
	private void log(String s) {
		Message msg = Message.obtain();
		msg.obj = s;
		handler.sendMessage(msg);
	}


	//下载按钮点击事件监听器
	class DownloadButtonOnClickListener implements OnClickListener {

		@Override
		public void onClick(View p1) {
			if (loadDriver()) {
				new Test().start();
			}
		}
	}

	//测试
	class Test extends Thread {

		@Override
		public void run() {
			
			long startTime = System.currentTimeMillis(); //获取开始时间
			
			String path = pathEditText.getText().toString();
			byte[] data = Utils.file2byte(Hex2Bin.conv(path).getAbsolutePath());
			log("程序大小(code length)：" + data.length);
			
			log("正在连续发送0x7F，等待单片机响应");
			if (!detect()) {
				log("连接失败");
				return;
			}
			log("连接成功");

			baudCheck();
			handshake();

			erase();
			
			flash(data);
			
			long endTime = System.currentTimeMillis(); //获取结束时间
			log("耗时：" + (endTime - startTime) + "ms");

		}
	}

	public boolean loadDriver() {
		MyApp.driver = new CH34xUARTDriver((UsbManager) getSystemService(Context.USB_SERVICE), this, ACTION_USB_PERMISSION);

		// 判断系统是否支持USB HOST
		if (MyApp.driver.UsbFeatureSupported()) {
			return openCH34x();
		} else {
			log("您的手机不支持USB HOST，请更换其他手机再试！");
		}

		return false;
	}

	private boolean openCH34x() {
		// 打开流程主要步骤为ResumeUsbList，UartInit
		int retval = MyApp.driver.ResumeUsbPermission();
		if (retval == 0) {
			// Resume usb device list
			// ResumeUsbList方法用于枚举CH34X设备以及打开相关设备
			retval = MyApp.driver.ResumeUsbList();
			if (retval == 0) {

				if (MyApp.driver.mDeviceConnection != null) {
					//对串口设备进行初始化操作
					if (!MyApp.driver.UartInit()) {
						log("Initialization failed!");
						return false;
					}
					log("Device opened");
					return setConfig();


				} else {	
					log("Open failed! Device Connection error.");	
				}

			} else {
				log("Open failed! Error Code:" + retval);
				MyApp.driver.CloseDevice();
			}
		} else {
			log("Open failed! Error Code:" + retval);
			MyApp.driver.CloseDevice();
		}

		return false;
	}


	private boolean setConfig() {
		//配置串口波特率，函数说明可参照编程手册
		boolean isSetConfig = MyApp.driver.SetConfig(baudRate, dataBit, stopBit, parity, flowControl);
		if (isSetConfig) {					
			log("Config successfully");
		} else {
			log("Config failed!");
		}
		return isSetConfig;
	}




	//发现
	boolean detect() {
		byte[] send = new byte[]{0x7F};
		Result result = null;
		byte[] recv = new byte[]{0x68};
		while (result == null) {
			try {
				//不断往串口发 0x7F，使单片机进入ISP模式
				MyApp.driver.WriteData(send, send.length);
			} catch (Exception e) {
				log(e.toString());
				return false;
			}
			result = waitingResponse(0.05, recv);
		}

		return true;
	}

	//测试波特率,设置波特率
	private void baudCheck() {
		log("测试波特率2400...");
		send(CMD_CHECK_BAUD, baudrate_chk_11M_2400);
		Result recv = waitingResponse();

		log("设置波特率2400...");
		send(CMD_CONFIRM_BAUD, baudrate_chg_11M_2400);
		Result recv2 = waitingResponse();

	}

	//握手
	private void handshake() {

		log("握手5次...");

		//发送5次握手包
		for (int i=0;i < 5;i++) {
			send(CMD_ACK, ack);
			Result recv = waitingResponse();
		}
	}


	//擦除
	private boolean erase() {

		log("擦除ROM...");
		send(CMD_ERASE, new byte[]{0x06, 0x33, 0x33, 0x33, 0x33, 0x33, 0x33});

		Result recv = waitingResponse();
		return true;

	}



	void flash(byte[] code) {
		ByteBuffer byteBuffer = ByteBuffer.allocate(1024);
		byteBuffer.put(code);
		byteBuffer.put(Utils.create((511 - (code.length - 1) % 512), (byte) 0xff));
		code = Utils.data(byteBuffer);


		for (int i = 0; i < code.length; i += 128) {
			//System.out.printf("Flash code region (%04X, %04X)\n", i, i + 127);
			byte[] addr = new byte[]{0, 0, (byte) (i >> 8), (byte) (i & 0xFF), 0, (byte) 128};
			byte[] sub = Utils.sub(code, i, i + 128);
			byteBuffer = ByteBuffer.allocate(1024);
			byteBuffer.put(addr).put(sub);
			send((byte) 0x00, Utils.data(byteBuffer));
			Result recv = waitingResponse();
//            assert recv.data[0] == Utils.sum(sub) % 256;
			//System.out.printf("progress=%.2f \n", (i + 128f) / code.length);
			log(String.format("下载进度:%d%%", (int)(100f * (i + 128f) / code.length)));
		}
	}


	Result waitingResponse() {

		//1. 包头：2字节，固定为：0x46，0xB9。
		//2. 标示：1字节，分两种，请求 ：0x6A；响应：0x68。
		return waitingResponse(3, new byte[]{0x46, (byte) 0xB9, 0x68});
	}

	// 等待响应结果，timeout超时时间，start指定的开头
	Result waitingResponse(double timeout, byte[] start) {

		byte[] readConn = readComm(start.length, (long) (timeout * 1000));
		if (!Arrays.equals(readConn, start)) {
			if (readConn.length > 0) {
				log("hex-->" + Utils.hex2String(readConn));
			}
			return null;
		}

		log("header-->" + Utils.hex2String(readConn));

		int chksum = start[start.length - 1];


		byte[] bytes = readComm(2);

		chksum += checksum(bytes);

		log("size-->" + Utils.hex2String(bytes));

		int dataLength = Utils.getInt(bytes[0], bytes[1]);


		bytes = readComm(dataLength - 3);
		log("data-->" + Utils.hex2String(bytes));
		//log("data size: " + bytes.length);


		chksum += checksum(Utils.sub(bytes, 0, -2));

		if ((chksum & 0xff) != (bytes[bytes.length - 2] & 0xff)) {
			log("checksum err.");
			log("计算结果：" + (chksum & 0xff));
			log("实际结果：" + (bytes[bytes.length - 2] & 0xff));
		}



		return new Result(bytes[0], Utils.sub(bytes, 1, -2));
	}


	protected byte[] readComm(int size) {
		return readComm(size, 3 * 1000);
	}

	// 读串口，size指定读的长度，time超时时间
	protected byte[] readComm(int size, long time) {
		long endTime = System.currentTimeMillis() + time;
		ByteBuffer byteBuffer = ByteBuffer.allocate(1024);
		while (byteBuffer.position() < size) {
			byte[] src = read(size - byteBuffer.position());
			byteBuffer.put(src);
			if (System.currentTimeMillis() > endTime) {
				break;
			}
			try {
				Thread.sleep(2);
			} catch (InterruptedException e) {
				e.printStackTrace();
			}
		}
		//log("readComm end.");
		return Utils.data(byteBuffer);
	}



	void send(byte cmd, byte[] dat) {

		ByteBuffer buf = ByteBuffer.allocate(1024);

		buf.put(new byte[]{0x46, (byte) 0xB9, 0x6A});
		//长度=标示：1字节+数据包长度：2字节+命令：1字节+内容0-134字节+校验：1字节+包尾：1字节
		int n = 1 + 2 + 1 + dat.length + 1 + 1;
		buf.put(new byte[]{(byte) (n >> 8), (byte) (n & 0xff), cmd});
		buf.put(dat);

		int chksum = checksum(Utils.sub(buf.array(), 2, buf.position()));

		buf.put(new byte[]{(byte) (chksum & 0xFF), 0x16});

		byte[] data = Utils.data(buf);
		log("send: " + Utils.hex2String(data));
		try {
			MyApp.driver.WriteData(data, data.length);
		} catch (Exception e) {
			e.printStackTrace();
		}
	}


	private byte[] read(int size) {
		byte[] buffer=new byte[size];
		int i = MyApp.driver.ReadData(buffer, size);
		if (i <= 0) {
			return new byte[0];
		}
		return Arrays.copyOfRange(buffer, 0, i);
	}

	//校验和
	int checksum(byte[] arr) {
		int s = 0;
		for (byte b : arr) {
			s += b & 0xff;
		}
		return s;
	}

	class Result {
		byte cmd;
		byte[] data;

		public Result(byte cmd, byte[] data) {
			this.cmd = cmd;
			this.data = data;
		}
	}




}
