package com.njust.lbs;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import android.Manifest;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.location.Location;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.os.Looper;
import android.os.Message;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;
import android.view.View.OnClickListener;


import com.baidu.location.BDLocation;
import com.baidu.location.BDLocationListener;
import com.baidu.location.LocationClient;
import com.baidu.location.LocationClientOption;
import com.baidu.mapapi.SDKInitializer;
import com.baidu.mapapi.map.BaiduMap;
import com.baidu.mapapi.map.MapStatusUpdate;
import com.baidu.mapapi.map.MapStatusUpdateFactory;
import com.baidu.mapapi.map.MapView;
import com.baidu.mapapi.map.MyLocationData;
import com.baidu.mapapi.model.LatLng;
import com.baidu.navisdk.adapter.BNOuterLogUtil;
import com.baidu.navisdk.adapter.BNRoutePlanNode;
import com.baidu.navisdk.adapter.BNaviCommonParams;
import com.baidu.navisdk.adapter.BaiduNaviManagerFactory;
import com.baidu.navisdk.adapter.IBNRoutePlanManager;
import com.baidu.navisdk.adapter.IBaiduNaviManager;
import com.baidu.navisdk.adapter.impl.BaiduNaviManager;
import com.baidu.navisdk.adapter.BNRoutePlanNode.CoordinateType;

//import com.baidu.navisdk.adapter.BNaviSettingManager;

import com.baidu.navisdk.adapter.BNRoutePlanNode.CoordinateType;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

public class MainActivity extends AppCompatActivity {


    //当前位置的BNRoutePlanNod(⊙﹏⊙)
    private BNRoutePlanNode Node;
    private double X,Y;




    private Button mWgsNaviBtn = null;
    private boolean hasInitSuccess = false;
    private BNRoutePlanNode mStartNode = null;
    private static final int NORMAL = 0;
    private static final int EXTERNAL = 1;
    static final String ROUTE_PLAN_NODE = "routePlanNode";
    private String mSDCardPath = null;
    private static final String APP_FOLDER_NAME = "BNSDKSimpleDemo";
    private static final String[] authBaseArr = {
            Manifest.permission.WRITE_EXTERNAL_STORAGE,
            Manifest.permission.ACCESS_FINE_LOCATION
    };
    private static final int authBaseRequestCode = 1;


    public static final String TAG = "LSB";
    public LocationClient mLocationClient;//定位SDK核心类
    //    private TextView positionText;
    private MapView mapView;//地图控件
    private BaiduMap baiduMap;//地图的总控制器
    private boolean isFirstLocate = true;//用于初始位置地图绘制

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
//        setContentView(R.layout.activity_main);//指定Activity对应的xml

        mLocationClient = new LocationClient(getApplicationContext());//获取全局Context参数
        mLocationClient.registerLocationListener(new MyLocationListener());//注册一个定位监听器 当获取到位置信息时会回调这个定位监听器
        SDKInitializer.initialize(getApplicationContext());//
        setContentView(R.layout.activity_main);
        mapView = findViewById(R.id.bmapView);//绑定
        baiduMap = mapView.getMap();//实例化
        baiduMap.setMyLocationEnabled(true);//光标显示当前位置
//        positionText = findViewById(R.id.position);//绑定

        //判断是否有各个权限 若没有则用List打包统一申请
        List<String> permissionList = new ArrayList<>();
        if (ContextCompat.checkSelfPermission(MainActivity.this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            permissionList.add(Manifest.permission.ACCESS_FINE_LOCATION);
        }
        if (ContextCompat.checkSelfPermission(MainActivity.this, Manifest.permission.READ_PHONE_STATE) != PackageManager.PERMISSION_GRANTED) {
            permissionList.add(Manifest.permission.READ_PHONE_STATE);
        }
        if (ContextCompat.checkSelfPermission(MainActivity.this, Manifest.permission.WRITE_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED) {
            permissionList.add(Manifest.permission.WRITE_EXTERNAL_STORAGE);
        }
        if (!permissionList.isEmpty()) {
            String[] permissions = permissionList.toArray(new String[permissionList.size()]);
            ActivityCompat.requestPermissions(MainActivity.this, permissions, 1);//申请权限
        } else {
            requestLocation();
        }


        Log.d(TAG, "进入导航模块");
        /*导航*/
        mWgsNaviBtn = findViewById(R.id.navi);
        initListener();
        if (initDirs()) {
            initNavi();
        }
        initLocation();

    }

    private void requestLocation() {
        initLocation();//实时定位
        mLocationClient.start();//开始定位 回调监听器
    }

    private void initLocation() {
        LocationClientOption option = new LocationClientOption();
        option.setScanSpan(5000);//设置扫描间隔为5s
//        option.setLocationMode(LocationClientOption.LocationMode.Device_Sensors);//强制使用GPS定位
        mLocationClient.setLocOption(option);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        mLocationClient.stop();//停止定位
        mapView.onDestroy();//关闭地图
        baiduMap.setMyLocationEnabled(false);//关闭光标
    }

    //权限相关
    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        switch (requestCode) {
            case 1:
                if (grantResults.length > 0) {
                    for (int result : grantResults) {
                        if (result != PackageManager.PERMISSION_GRANTED) {
                            Toast.makeText(this, "必须同意权限", Toast.LENGTH_SHORT).show();
                            finish();//若用户拒绝开启权限则关闭程序
                            return;
                        }
                    }
                    requestLocation();//获取权限后开始定位
                } else {
                    Toast.makeText(this, "发生未知错误", Toast.LENGTH_SHORT).show();
                    finish();
                }
                break;
            default:
        }
    }

    //定位相关
    public class MyLocationListener implements BDLocationListener {

        @Override
        public void onReceiveLocation(BDLocation bdLocation) {
            StringBuilder currentPosition = new StringBuilder();
            currentPosition.append("纬度：").append(bdLocation.getLatitude()).append("\n");
            currentPosition.append("经度：").append(bdLocation.getLongitude()).append("\n");
            currentPosition.append("定位方式：");
            if (bdLocation.getLocType() == BDLocation.TypeGpsLocation) {
                currentPosition.append("GPS");
            } else if (bdLocation.getLocType() == BDLocation.TypeNetWorkLocation) {
                currentPosition.append("网络");
            }
//            positionText.setText(currentPosition);
            Log.d(TAG, "---" + currentPosition);


            //2019.07.16 16:30
            Node = new BNRoutePlanNode.Builder()
                    .latitude(bdLocation.getLatitude())
                    .longitude(bdLocation.getLongitude())
                    .name("玄武区派出所")
                    .description("玄武区派出所")
                    .coordinateType(CoordinateType.WGS84)
                    .build();
            X=bdLocation.getLatitude();
            Y=bdLocation.getLongitude();

            if (bdLocation.getLocType() == BDLocation.TypeGpsLocation || bdLocation.getLocType() == BDLocation.TypeNetWorkLocation) {
                navigateTo(bdLocation);
            }
        }

    }

    //地图绘制相关
    @Override
    protected void onResume() {
        super.onResume();
        mapView.onResume();
    }

    @Override
    protected void onPause() {
        super.onPause();
        mapView.onPause();
    }


    //定位到地图
    private void navigateTo(BDLocation location) {
        if (isFirstLocate) {
            LatLng ll = new LatLng(location.getLatitude(), location.getLongitude());//利用LatLng类实现地图定位到自己的位置
            MapStatusUpdate update = MapStatusUpdateFactory.newLatLng(ll);
            baiduMap.animateMapStatus(update);
            update = MapStatusUpdateFactory.zoomTo(18.5f);//设置缩放精度 范围12~19
            baiduMap.animateMapStatus(update);
            isFirstLocate = false;
        }
        //光标
        MyLocationData.Builder locationBuilder = new MyLocationData.Builder();
        locationBuilder.latitude(location.getLatitude());//获取经纬度
        locationBuilder.longitude(location.getLongitude());
        MyLocationData locationData = locationBuilder.build();
        baiduMap.setMyLocationData(locationData);

    }

    /*导航*/
    private void initListener() {
        if (mWgsNaviBtn != null) {
            mWgsNaviBtn.setOnClickListener(new OnClickListener() {

                @Override
                public void onClick(View arg0) {
                    if (BaiduNaviManagerFactory.getBaiduNaviManager().isInited()) {
                        Log.d(TAG, "点击成功");

                        calRoutePlanNode(CoordinateType.WGS84);

                    }
                }

            });
        }
    }

    private void calRoutePlanNode(final int coType) {
        Log.d(TAG, "定位坐标");

        if (!hasInitSuccess) {
            Toast.makeText(MainActivity.this.getApplicationContext(), "还未初始化!", Toast
                    .LENGTH_SHORT).show();
        }

        BNRoutePlanNode sNode = new BNRoutePlanNode.Builder()
                .latitude(50.05087)
                .longitude(116.30142)
                .name("百度大厦")
                .description("百度大厦")
                .coordinateType(coType)
                .build();
        BNRoutePlanNode eNode = new BNRoutePlanNode.Builder()
                .latitude(39.90882)
                .longitude(116.39750)
                .name("北京天安门")
                .description("北京天安门")
                .coordinateType(coType)
                .build();
//        switch (coType) {
//            case CoordinateType.WGS84: {
//                sNode = new BNRoutePlanNode.Builder()
//                        .latitude(40.050969)
//                        .longitude(116.300821)
//                        .name("百度大厦")
//                        .description("百度大厦")
//                        .coordinateType(coType)
//                        .build();
//                eNode = new BNRoutePlanNode.Builder()
//                        .latitude(39.908749)
//                        .longitude(116.397491)
//                        .name("北京天安门")
//                        .description("北京天安门")
//                        .coordinateType(coType)
//                        .build();
//                break;
//            }
//            default:
//                break;
//        }

        mStartNode = sNode;
//        mStartNode=Node;
        routePlanToNavi(sNode, eNode, NORMAL);

    }


    /*算路设置起、终点，算路偏好，是否模拟导航等参数，然后在回调函数中设置跳转至诱导。*/

    private void routePlanToNavi(BNRoutePlanNode sNode, BNRoutePlanNode eNode, final int from) {
        Log.d(TAG, "算路开始");

        List<BNRoutePlanNode> list = new ArrayList<>();
        list.add(sNode);
        list.add(eNode);

        BaiduNaviManagerFactory.getCommonSettingManager().setCarNum(this, "粤B66666");
        BaiduNaviManagerFactory.getRoutePlanManager().routeplanToNavi(
                list,
                IBNRoutePlanManager.RoutePlanPreference.ROUTE_PLAN_PREFERENCE_DEFAULT,
                null,
                new Handler(Looper.getMainLooper()) {
                    @Override
                    public void handleMessage(Message msg) {
                        switch (msg.what) {
                            case IBNRoutePlanManager.MSG_NAVI_ROUTE_PLAN_START:
                                Toast.makeText(MainActivity.this.getApplicationContext(),
                                        "算路开始", Toast.LENGTH_SHORT).show();
                                break;
                            case IBNRoutePlanManager.MSG_NAVI_ROUTE_PLAN_SUCCESS:
//                                Toast.makeText(MainActivity.this.getApplicationContext(),
//                                        "算路成功", Toast.LENGTH_SHORT).show();
                                // 躲避限行消息
                                Bundle infoBundle = (Bundle) msg.obj;
                                if (infoBundle != null) {
                                    String info = infoBundle.getString(
                                            BNaviCommonParams.BNRouteInfoKey.TRAFFIC_LIMIT_INFO
                                    );
                                    Log.d("OnSdkDemo", "info** = " + info);
                                }
                                break;
                            case IBNRoutePlanManager.MSG_NAVI_ROUTE_PLAN_FAILED:
                                Toast.makeText(MainActivity.this.getApplicationContext(),
                                        "算路失败", Toast.LENGTH_SHORT).show();
                                BaiduNaviManagerFactory.getRoutePlanManager()
                                        .removeRequestByHandler(this);
                                break;
                            case IBNRoutePlanManager.MSG_NAVI_ROUTE_PLAN_TO_NAVI:
//                                Toast.makeText(MainActivity.this.getApplicationContext(),
//                                        "算路成功准备进入导航", Toast.LENGTH_SHORT).show();

                                Log.d("OnSdkDemo", "info** = " + "算路成功准备进入导航");


                                Intent intent = null;
//                                if (from == NORMAL) {
//                                    intent = new Intent(MainActivity.this,
//                                            GuideActivity.class);
//                                }  else if (from == EXTERNAL) {
//                                    intent = new Intent(MainActivity.this,
//                                            ExtGpsActivity.class);
//                                }
                                intent = new Intent(MainActivity.this, GuideActivity.class);
                                Bundle bundle = new Bundle();
                                bundle.putSerializable(ROUTE_PLAN_NODE, mStartNode);
                                intent.putExtras(bundle);
                                startActivity(intent);
                                BaiduNaviManagerFactory.getRoutePlanManager()
                                        .removeRequestByHandler(this);
                                break;
                            default:
                                // nothing
                                break;
                        }
                    }
                });
    }

    /*初始化SD卡，在SD卡路径下新建文件夹：App目录名，文件中包含了很多东西，比如log、cache等等*/
    private boolean initDirs() {
        mSDCardPath = getSdcardDir();
        if (mSDCardPath == null) {
            return false;
        }
        File f = new File(mSDCardPath, APP_FOLDER_NAME);
        if (!f.exists()) {
            try {
                f.mkdir();
            } catch (Exception e) {
                e.printStackTrace();
                return false;
            }
        }
        return true;
    }

    private String getSdcardDir() {
        if (Environment.getExternalStorageState().equalsIgnoreCase(Environment.MEDIA_MOUNTED)) {
            return Environment.getExternalStorageDirectory().toString();
        }
        return null;
    }

    private boolean hasBasePhoneAuth() {
        PackageManager pm = this.getPackageManager();
        for (String auth : authBaseArr) {
            if (pm.checkPermission(auth, this.getPackageName()) != PackageManager
                    .PERMISSION_GRANTED) {
                return false;
            }
        }
        return true;
    }
//    private void initTTS() {
//        // 使用内置TTS
//        BaiduNaviManagerFactory.getTTSManager().initTTS(getApplicationContext(),
//                getSdcardDir(), APP_FOLDER_NAME, NormalUtils.getTTSAppID());
//
//    }

    private void initSetting() {
//        /**
//         * 日夜模式 1：自动模式 2：白天模式 3：夜间模式
//         */
//        BNaviSettingManager.setDayNightMode(BNaviSettingManager.DayNightMode.DAY_NIGHT_MODE_DAY);
//        /**
//         * 设置全程路况显示
//         */
//        BNaviSettingManager.setShowTotalRoadConditionBar(BNaviSettingManager.PreViewRoadCondition.ROAD_CONDITION_BAR_SHOW_ON);
//        /**
//         * 设置语音播报模式
//         */
//        BNaviSettingManager.setVoiceMode(BNaviSettingManager.VoiceMode.Veteran);
//        /**
//         * 设置省电模式
//         */
//        BNaviSettingManager.setPowerSaveMode(BNaviSettingManager.PowerSaveMode.DISABLE_MODE);
//        /**
//         * 设置实时路况条
//         */
//        BNaviSettingManager.setRealRoadCondition(BNaviSettingManager.RealRoadCondition.NAVI_ITS_ON);
    }


    private void initNavi() {
        // 申请权限
        if (android.os.Build.VERSION.SDK_INT >= 23) {
            if (!hasBasePhoneAuth()) {
                this.requestPermissions(authBaseArr, authBaseRequestCode);
                return;
            }
        }

        if (BaiduNaviManagerFactory.getBaiduNaviManager().isInited()) {
            hasInitSuccess = true;
            return;
        }

        BaiduNaviManagerFactory.getBaiduNaviManager().init(this,
                mSDCardPath, APP_FOLDER_NAME, new IBaiduNaviManager.INaviInitListener() {

                    @Override
                    public void onAuthResult(int status, String msg) {
                        String result;
                        if (0 == status) {
                            result = "key校验成功!";
                        } else {
                            result = "key校验失败, " + msg;
                        }
                        Toast.makeText(MainActivity.this, result, Toast.LENGTH_LONG).show();
                    }

                    @Override
                    public void initStart() {
                        Toast.makeText(MainActivity.this.getApplicationContext(),
                                "百度导航引擎初始化开始", Toast.LENGTH_SHORT).show();
                    }

                    @Override
                    public void initSuccess() {
                        Toast.makeText(MainActivity.this.getApplicationContext(),
                                "百度导航引擎初始化成功", Toast.LENGTH_SHORT).show();
                        hasInitSuccess = true;
                        // 初始化tts
//                        initTTS();
                        initSetting();
                    }

                    @Override
                    public void initFailed(int errCode) {
                        Toast.makeText(MainActivity.this.getApplicationContext(),
                                "百度导航引擎初始化失败 " + errCode, Toast.LENGTH_SHORT).show();
                    }
                });
    }


}
