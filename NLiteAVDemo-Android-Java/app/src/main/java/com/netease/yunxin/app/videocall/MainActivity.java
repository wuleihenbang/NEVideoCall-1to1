// Copyright (c) 2022 NetEase, Inc. All rights reserved.
// Use of this source code is governed by a MIT license that can be
// found in the LICENSE file.

package com.netease.yunxin.app.videocall;

import android.Manifest;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import android.text.TextUtils;
import android.util.Log;
import android.view.View;
import android.widget.ImageView;
import android.widget.RelativeLayout;
import android.widget.TextView;
import android.widget.Toast;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import com.netease.lava.nertc.foreground.ForegroundKit;
import com.netease.lava.nertc.sdk.NERtc;
import com.netease.lava.nertc.sdk.NERtcEx;
import com.netease.nimlib.sdk.NIMClient;
import com.netease.nimlib.sdk.v2.V2NIMError;
import com.netease.nimlib.sdk.v2.auth.V2NIMLoginListener;
import com.netease.nimlib.sdk.v2.auth.V2NIMLoginService;
import com.netease.nimlib.sdk.v2.auth.enums.V2NIMLoginClientChange;
import com.netease.nimlib.sdk.v2.auth.enums.V2NIMLoginStatus;
import com.netease.nimlib.sdk.v2.auth.model.V2NIMKickedOfflineDetail;
import com.netease.nimlib.sdk.v2.auth.model.V2NIMLoginClient;
import com.netease.yunxin.app.videocall.login.model.AuthManager;
import com.netease.yunxin.app.videocall.login.ui.LoginActivity;
import com.netease.yunxin.app.videocall.nertc.biz.CallOrderManager;
import com.netease.yunxin.app.videocall.nertc.ui.CallModeType;
import com.netease.yunxin.app.videocall.nertc.ui.NERTCSelectCallUserActivity;
import com.netease.yunxin.app.videocall.nertc.ui.SettingActivity;
import com.netease.yunxin.nertc.ui.CallKitUI;
import com.netease.yunxin.nertc.ui.CallKitUIOptions;
import com.netease.yunxin.nertc.ui.NECallUILanguage;
import com.netease.yunxin.nertc.ui.base.TransHelper;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class MainActivity extends AppCompatActivity {
  private static final int NOTIFICATION_PERMISSION_REQUEST_CODE = 1001;
  private static final int CODE_REQUEST_INVITE_USERS = 9101;

  private TextView tvVersion;

  @Override
  protected void onCreate(Bundle savedInstanceState) {
    super.onCreate(savedInstanceState);
    setContentView(R.layout.activity_main);
    checkAndRequestNotificationPermission(this);
    initView();
    checkLogin();
    initG2();
    dumpTest();
  }

  private void dumpTest() {
    if (BuildConfig.DEBUG) {
      findViewById(R.id.btn)
          .setOnClickListener(
              v -> {
                Toast.makeText(MainActivity.this, "开始dump音频", Toast.LENGTH_LONG).show();
                NERtcEx.getInstance().startAudioDump();
              });
      findViewById(R.id.btn2)
          .setOnClickListener(
              v -> {
                Toast.makeText(
                        MainActivity.this,
                        "dump已结束，请到/sdcard/Android/data/com.netease.videocall.demo/files/dump目录查看dump文件",
                        Toast.LENGTH_LONG)
                    .show();
                NERtcEx.getInstance().stopAudioDump();
              });
    } else {
      findViewById(R.id.btn).setVisibility(View.GONE);
      findViewById(R.id.btn2).setVisibility(View.GONE);
    }
  }

  @Override
  protected void onResume() {
    super.onResume();
  }

  @Override
  protected void onDestroy() {
    super.onDestroy();
  }

  private void initG2() {

    NIMClient.getService(V2NIMLoginService.class)
        .addLoginListener(
            new V2NIMLoginListener() {
              @Override
              public void onLoginStatus(V2NIMLoginStatus status) {
                if (status == V2NIMLoginStatus.V2NIM_LOGIN_STATUS_LOGINED) {
                  if (TextUtils.equals(
                      AuthManager.getInstance().getUserModel().imAccid,
                      CallKitUI.INSTANCE.getCurrentUserAccId())) {
                    return;
                  }
                  Log.e("======", "init " + AuthManager.getInstance().getUserModel().imAccid);
                  CallKitUIOptions options =
                      new CallKitUIOptions.Builder()
                          // 音视频通话 sdk appKey，用于通话中使用
                          .rtcAppKey(BuildConfig.APP_KEY)
                          // 当前用户 accId
                          .currentUserAccId(AuthManager.getInstance().getUserModel().imAccid)
                          .currentUserRtcUId(SettingActivity.RTC_CHANNEL_UID)
                          // 通话接听成功的超时时间单位 毫秒，默认30s
                          .timeOutMillisecond(30 * 1000L)
                          // 当系统版本为 Android Q及以上时，若应用在后台系统限制不直接展示页面
                          // 而是展示 notification，通过点击 notification 跳转呼叫页面
                          // 此处为 notification 相关配置，如图标，提示语等。
                          .notificationConfigFetcher(new DemoSelfNotificationConfigFetcher<>())
                          .notificationConfigFetcherForGroup(
                              new DemoSelfNotificationConfigFetcher<>())
                          // 收到被叫时若 app 在后台，在恢复到前台时是否自动唤起被叫页面，默认为 true
                          .resumeBGInvitation(true)
                          .enableGroup(true)
                          .enableAutoJoinWhenCalled(SettingActivity.ENABLE_AUTO_JOIN)
                          // 设置用户信息
                          .userInfoHelper(new SelfUserInfoHelper())
                          // rtc 初始化模式
                          .initRtcMode(SettingActivity.RTC_INIT_MODE)
                          // 注册自定义通话页面
                          .p2pVideoActivity(TestActivity.class)
                          .p2pAudioActivity(TestActivity.class)
                          // 主叫加入 rtc 的时机
                          .joinRtcWhenCall(SettingActivity.ENABLE_JOIN_RTC_WHEN_CALL)
                          .contactSelector(
                              (context, groupId, strings, listNEResultObserver) -> {
                                if (listNEResultObserver == null) {
                                  return null;
                                }
                                TransHelper.launchTask(
                                    context,
                                    CODE_REQUEST_INVITE_USERS,
                                    (innerContext, code) -> {
                                      NERTCSelectCallUserActivity.startSelectUser(
                                          innerContext,
                                          CODE_REQUEST_INVITE_USERS,
                                          CallModeType.RTC_GROUP_INVITE,
                                          strings);
                                      return null;
                                    },
                                    intentResultInfo -> {
                                      if (intentResultInfo == null
                                          || intentResultInfo.getValue() == null) {
                                        return null;
                                      }
                                      Intent data = intentResultInfo.getValue();
                                      if (intentResultInfo.getSuccess()) {
                                        ArrayList<String> selectorList =
                                            data.getStringArrayListExtra(
                                                NERTCSelectCallUserActivity.KEY_CALL_USER_LIST);
                                        listNEResultObserver.onResult(selectorList);
                                      }
                                      return null;
                                    });
                                return null;
                              })
                          .language(NECallUILanguage.AUTO)
                          .build();
                  // 初始化
                  CallKitUI.init(getApplicationContext(), options);
                  SettingActivity.toInit();
                }
              }

              @Override
              public void onLoginFailed(V2NIMError error) {}

              @Override
              public void onKickedOffline(V2NIMKickedOfflineDetail detail) {}

              @Override
              public void onLoginClientChanged(
                  V2NIMLoginClientChange change, List<V2NIMLoginClient> clients) {}
            });
  }

  private void checkLogin() {
    if (AuthManager.getInstance().isLogin()) {
      return;
    }

    //此处注册之后会立刻回调一次
    NIMClient.getService(V2NIMLoginService.class)
        .addLoginListener(
            new V2NIMLoginListener() {
              @Override
              public void onLoginStatus(V2NIMLoginStatus status) {
                if (status == V2NIMLoginStatus.V2NIM_LOGIN_STATUS_LOGINED) {
                  AuthManager.getInstance().setLogin(true);
                  CallOrderManager.getInstance().init();
                }
              }

              @Override
              public void onLoginFailed(V2NIMError error) {}

              @Override
              public void onKickedOffline(V2NIMKickedOfflineDetail detail) {}

              @Override
              public void onLoginClientChanged(
                  V2NIMLoginClientChange change, List<V2NIMLoginClient> clients) {}
            });
    if (NIMClient.getService(V2NIMLoginService.class).getLoginStatus()
        != V2NIMLoginStatus.V2NIM_LOGIN_STATUS_LOGINED) {
      if (AuthManager.getInstance().getUserModel() != null
          && !TextUtils.isEmpty(AuthManager.getInstance().getUserModel().imAccid)
          && !TextUtils.isEmpty(AuthManager.getInstance().getUserModel().imToken)) {
        NIMClient.getService(V2NIMLoginService.class)
            .login(
                AuthManager.getInstance().getUserModel().imAccid,
                AuthManager.getInstance().getUserModel().imToken,
                null,
                null,
                null);
      }
    }
  }

  private void initView() {
    ImageView ivAccountIcon = findViewById(R.id.iv_account);
    RelativeLayout rlyVideoCall = findViewById(R.id.rly_video_call);
    RelativeLayout rlyConvergedCall = findViewById(R.id.rly_converged_call);
    RelativeLayout rlyGroupCall = findViewById(R.id.rly_group_call);

    tvVersion = findViewById(R.id.tv_version);

    ivAccountIcon.setOnClickListener(
        view -> {
          if (AuthManager.getInstance().isLogin()) {
            showLogoutDialog();
          } else {
            LoginActivity.startLogin(this);
          }
        });

    rlyVideoCall.setOnClickListener(
        view -> {
          if (!AuthManager.getInstance().isLogin()) {
            LoginActivity.startLogin(this);
          } else {
            NERTCSelectCallUserActivity.startSelectUser(this, CallModeType.RTC_1V1_VIDEO_CALL);
          }
        });

    rlyGroupCall.setOnClickListener(
        v -> {
          if (!AuthManager.getInstance().isLogin()) {
            LoginActivity.startLogin(this);
          } else {
            NERTCSelectCallUserActivity.startSelectUser(
                this,
                CallModeType.RTC_GROUP_CALL,
                Collections.singletonList(AuthManager.getInstance().getUserModel().imAccid));
          }
        });
    rlyGroupCall.setVisibility(View.VISIBLE);

    rlyConvergedCall.setOnClickListener(
        v -> {
          if (!AuthManager.getInstance().isLogin()) {
            LoginActivity.startLogin(this);
          } else {
            NERTCSelectCallUserActivity.startSelectUser(this, CallModeType.PSTN_1V1_AUDIO_CALL);
          }
        });
    rlyConvergedCall.setVisibility(View.GONE);

    RelativeLayout rlPermission = findViewById(R.id.rl_goto_permission_setting);
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
      rlPermission.setVisibility(View.VISIBLE);
    } else {
      rlPermission.setVisibility(View.GONE);
    }
    rlPermission.setOnClickListener(v -> ForegroundKit.getInstance(this).requestFloatPermission());

    initVersionInfo();
  }

  private void initVersionInfo() {
    String versionInfo =
        "NIM sdk version:"
            + NIMClient.getSDKVersion()
            + "\nnertc sdk version:"
            + NERtc.version().versionName
            + "\ncallKit version:"
            + CallKitUI.INSTANCE.currentVersion();
    tvVersion.setText(versionInfo);
  }

  private void showLogoutDialog() {
    final AlertDialog.Builder confirmDialog = new AlertDialog.Builder(MainActivity.this);
    confirmDialog.setTitle("注销账户:" + AuthManager.getInstance().getUserModel().mobile);
    confirmDialog.setMessage("确认注销当前登录账号？");
    confirmDialog.setPositiveButton(
        "是",
        (dialog, which) -> {
          AuthManager.getInstance().logout();
          Toast.makeText(MainActivity.this, "已经退出登录", Toast.LENGTH_LONG).show();
        });
    confirmDialog.setNegativeButton("否", (dialog, which) -> {});

    confirmDialog.show();
  }

  private void checkAndRequestNotificationPermission(Context context) {
    // 检查是否需要请求通知权限
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
      if (ContextCompat.checkSelfPermission(context, android.Manifest.permission.POST_NOTIFICATIONS)
          != PackageManager.PERMISSION_GRANTED) {
        // 请求权限
        ActivityCompat.requestPermissions(
            this,
            new String[] {Manifest.permission.POST_NOTIFICATIONS},
            NOTIFICATION_PERMISSION_REQUEST_CODE);
      }
    }
  }
}
