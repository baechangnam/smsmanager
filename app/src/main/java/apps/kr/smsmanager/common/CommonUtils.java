// 공통으로 사용하는 메소드

package apps.kr.smsmanager.common;

import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;

import android.net.ConnectivityManager;
import android.net.NetworkInfo;

import android.provider.Settings;
import android.util.Base64;
import android.util.DisplayMetrics;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.widget.ListAdapter;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;

import com.google.android.material.snackbar.Snackbar;

import java.net.Inet4Address;
import java.net.InetAddress;
import java.net.NetworkInterface;
import java.net.SocketException;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.Enumeration;
import java.util.Locale;
import java.util.SimpleTimeZone;


public class CommonUtils {
	boolean isInternetWiMax = false;
	boolean isInternetWiFi = false;
	boolean isInternetMobile = false;
	private static CommonUtils current = null;
	String arrays[];
	TextView textView;


	public static String getDeviceID(Context context){
		String result = null;
		try {
			String androidId = "" + Settings.Secure.getString(context.getContentResolver(), Settings.Secure.ANDROID_ID);
			androidId = Base64.encodeToString(androidId.getBytes("UTF-8"), Base64.DEFAULT);

			result = androidId.trim();
		} catch (Exception e) {
			e.printStackTrace();
		}

		return result;
	}

	public static long DateToMillHH(String date) {
		String pattern = "yyyy-MM-dd HH:mm:ss";
		SimpleDateFormat formatter = new SimpleDateFormat(pattern);
		Date trans_date = null;
		try {
			trans_date = formatter.parse(date);
		} catch (ParseException e) {
		}
		return trans_date.getTime();
	}

	public static String DateToMillHHAA() {
		Calendar cal = Calendar.getInstance(new SimpleTimeZone(0x1ee6280, "KST"));
		Date date = cal.getTime();

		String pattern = "MM.dd a HH:mm:ss";
		SimpleDateFormat formatter = new SimpleDateFormat(pattern);

		return formatter.format(date.getTime());
	}

	private String getMonthAgoDate() {
		Calendar cal = Calendar.getInstance(new SimpleTimeZone(0x1ee6280, "KST"));
		cal.add(Calendar.MONTH ,1); // 한달전 날짜 가져오기
		Date monthago = cal.getTime();
		SimpleDateFormat formatter = new SimpleDateFormat("yyyy-MM-dd", Locale.getDefault());
		return formatter.format(monthago);
	}




	public static CommonUtils getInstance() {
		if (current == null) {
			current = new CommonUtils();
		}
		return current;
	}



	public static boolean IsStringInt(String s) {
		try {
			Integer.parseInt(s);
			return true;
		} catch (NumberFormatException e) {
			return false;
		}
	}

  // 아이피 정보
	public String getLocalIpAddress() {
		try {
			for (Enumeration en = NetworkInterface.getNetworkInterfaces(); en.hasMoreElements();) {
				NetworkInterface intf = (NetworkInterface) en.nextElement();
				for (Enumeration enumIpAddr = intf.getInetAddresses(); enumIpAddr.hasMoreElements();) {
					InetAddress inetAddress = (InetAddress) enumIpAddr.nextElement();
					if (!inetAddress.isLoopbackAddress()) {
						if (inetAddress instanceof Inet4Address) {
							return inetAddress.getHostAddress().toString();
						}

					}
				}
			}
		} catch (SocketException ex) {
		}
		return "";
	}




  //네트워크 체크

	public boolean isNetwork(Context context) {
		boolean netCheck = false;
		ConnectivityManager cm = (ConnectivityManager) context
				.getSystemService(Context.CONNECTIVITY_SERVICE);
		if (cm.getActiveNetworkInfo() != null) {
			NetworkInfo activeNetwork = cm.getActiveNetworkInfo();

			switch (activeNetwork.getType()) {
			case ConnectivityManager.TYPE_WIMAX: // 4g
				isInternetWiMax = true;
				netCheck = true;
				break;
			case ConnectivityManager.TYPE_WIFI: // wifi

				isInternetWiFi = true;
				netCheck = true;
				break;
			case ConnectivityManager.TYPE_MOBILE: // 3g

				isInternetMobile = true;
				netCheck = true;
				break;
			}
		} else {
			return false;
		}
		return netCheck;
	}





	public static String getResourseStr(int resource, Context context ) {
		return  context.getResources().getString(resource);

	}

	public void showAlertList(String array[], String title, Context context,
                              TextView txt) {
		arrays = array;
		textView = txt;

		AlertDialog.Builder builder = new AlertDialog.Builder(context);
		builder.setTitle(title);
		builder.setItems(arrays, new DialogInterface.OnClickListener() {
			public void onClick(DialogInterface dialog, int pos) {
				dialog.dismiss();
				textView.setText(arrays[pos]);

			}
		});
		AlertDialog alert = builder.create();
		alert.show();

	}

	public static void showSnackBar(View view,String msg) {
		Snackbar.make(view , msg , Snackbar.LENGTH_SHORT).show();
	}


	public static void showAlert(String msg, Context context) {
		AlertDialog.Builder adialog = new AlertDialog.Builder(context);
		adialog.setMessage(msg).setTitle("")
				.setPositiveButton("확인", new DialogInterface.OnClickListener() {
					@Override
					public void onClick(DialogInterface dialog, int which) {
						dialog.cancel();
					}
				});
		AlertDialog alert = adialog.create();
		alert.show();
	}

	public static String getVersionName(Context context) {
		try {
			PackageInfo pi = context.getPackageManager().getPackageInfo(context.getPackageName(), 0);
			return pi.versionName;
		} catch (PackageManager.NameNotFoundException e) {
			return null;
		}
	}


	public static void showToast(String msg, Context context) {
		Toast.makeText(context, msg, Toast.LENGTH_SHORT).show();
	}

	public static boolean isPackageInstalled(String packageName, PackageManager packageManager) {
		try {
			packageManager.getPackageInfo(packageName, 0);
			return true;
		} catch (PackageManager.NameNotFoundException e) {
			return false;
		}
	}


	public int getDisplyHeight(Context context) {
		DisplayMetrics metrics = new DisplayMetrics();
		WindowManager windowManager = (WindowManager) context
				.getSystemService(Context.WINDOW_SERVICE);
		windowManager.getDefaultDisplay().getMetrics(metrics);
		return metrics.heightPixels;

	}

	public int getDisplyWidth(Context context) {
		DisplayMetrics metrics = new DisplayMetrics();
		WindowManager windowManager = (WindowManager) context
				.getSystemService(Context.WINDOW_SERVICE);
		windowManager.getDefaultDisplay().getMetrics(metrics);
		return metrics.widthPixels;

	}
	



	public boolean checkRooting(){
		boolean check=true;
		try {
			Runtime.getRuntime().exec("su");

		} catch ( Exception e) {
			check=false;
		}

		return check;


	}


	public static void setListViewHeightBasedOnChildren(ListView listView) {
		ListAdapter listAdapter = listView.getAdapter();
		if (listAdapter == null) {
			// pre-condition
			return;
		}

		int totalHeight = 0;
		int desiredWidth = View.MeasureSpec.makeMeasureSpec(listView.getWidth(), View.MeasureSpec.AT_MOST);

		for (int i = 0; i < listAdapter.getCount(); i++) {
			View listItem = listAdapter.getView(i, null, listView);
			listItem.measure(desiredWidth, View.MeasureSpec.UNSPECIFIED);
			totalHeight += listItem.getMeasuredHeight();
		}

		ViewGroup.LayoutParams params = listView.getLayoutParams();
		params.height = totalHeight + (listView.getDividerHeight() * (listAdapter.getCount() - 1));
		listView.setLayoutParams(params);
		listView.requestLayout();
	}






	private static class TIME_MAXIMUM{
		public static final int SEC = 60;
		public static final int MIN = 60;
		public static final int HOUR = 24;
		public static final int DAY = 30;
		public static final int MONTH = 12;
	}

	public static String formatTimeString(long regTime) {
		long curTime = System.currentTimeMillis();
		long diffTime = (curTime - regTime) / 1000;
		String msg = null;
		if (diffTime < TIME_MAXIMUM.SEC) {
			msg = "방금 전";
		} else if ((diffTime /= TIME_MAXIMUM.SEC) < TIME_MAXIMUM.MIN) {
			msg = diffTime + "분 전";
		} else if ((diffTime /= TIME_MAXIMUM.MIN) < TIME_MAXIMUM.HOUR) {
			msg = (diffTime) + "시간 전";
		} else if ((diffTime /= TIME_MAXIMUM.HOUR) < TIME_MAXIMUM.DAY) {
			msg = (diffTime) + "일 전";
		} else if ((diffTime /= TIME_MAXIMUM.DAY) < TIME_MAXIMUM.MONTH) {
			msg = (diffTime) + "달 전";
		} else {
			msg = (diffTime) + "년 전";
		}
		return msg;
	}

	// /////////////////////////////////////////////////////////////////
	
	// //////////////////// density ////////////////////////////////////

	public float getDensity(Context context) {
		DisplayMetrics dm = new DisplayMetrics();
		WindowManager windowManager = (WindowManager) context
				.getSystemService(Context.WINDOW_SERVICE);
		windowManager.getDefaultDisplay().getMetrics(dm);
		float density = dm.density;

		return density;

	}

	public static  String getTimes(Context context) { //현재시간
		SimpleDateFormat mSimpleDateFormat = new SimpleDateFormat( "yyyyMMdd", Locale.KOREA );
		Date currentTime = new Date();
		String mTime = mSimpleDateFormat.format ( currentTime );


		return mTime;

	}

	public static  String getTimes1(Context context) { //현재시간
		SimpleDateFormat mSimpleDateFormat = new SimpleDateFormat( "yyyy-MM-dd", Locale.KOREA );
		Date currentTime = new Date();
		String mTime = mSimpleDateFormat.format ( currentTime );


		return mTime;

	}

}
