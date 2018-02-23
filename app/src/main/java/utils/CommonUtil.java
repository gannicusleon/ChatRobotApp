package utils;

import android.content.pm.ApplicationInfo;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

import entity.AppBean;

/**
 * 公用工具类
 */

public class CommonUtil {

    public static List<AppBean> getAllApk() {
        List<AppBean>appBeanList=new ArrayList<>();
        AppBean bean=null;
        PackageManager packageManager= UIUtils.getContext().getPackageManager();
        List<PackageInfo> list=packageManager.getInstalledPackages(0);
        for (PackageInfo p:list) {
            bean=new AppBean();
            bean.setAppIcon(p.applicationInfo.loadIcon(packageManager));
            bean.setAppName(packageManager.getApplicationLabel(p.applicationInfo).toString());
            bean.setAppPackageName(p.applicationInfo.packageName);
            bean.setApkPath(p.applicationInfo.sourceDir);
            File file=new File(p.applicationInfo.sourceDir);
            bean.setAppSize((int) file.length());
            int flags=p.applicationInfo.flags;
            //判断是否是属于系统的apk
            if ((flags& ApplicationInfo.FLAG_SYSTEM)!=0){
                bean.setSystem(true);
            }else {
                bean.setSd(true);
            }
            appBeanList.add(bean);

        }
        return appBeanList;
    }
}
