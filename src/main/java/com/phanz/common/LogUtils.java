package com.phanz.common;

import android.os.Environment;
import android.text.TextUtils;

import com.elvishew.xlog.LogConfiguration;
import com.elvishew.xlog.LogLevel;
import com.elvishew.xlog.XLog;
import com.elvishew.xlog.flattener.ClassicFlattener;
import com.elvishew.xlog.printer.AndroidPrinter;
import com.elvishew.xlog.printer.Printer;
import com.elvishew.xlog.printer.file.FilePrinter;
import com.elvishew.xlog.printer.file.backup.NeverBackupStrategy;
import com.elvishew.xlog.printer.file.naming.ChangelessFileNameGenerator;

import java.io.File;
import java.io.FilenameFilter;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;

/**
 * Created by phanz on 2017/12/3.
 */

public class LogUtils {

    private static final String BASE_LOCATION = Environment.getExternalStorageDirectory().getPath() + File.separator + "BluetoothTool" + File.separator;
    public static final String LOG_LOCATION = BASE_LOCATION + "log" + File.separator;
    private static final String FORMAT = "yyyy-MM-dd";
    private static final String SPLIT = "_";
    private static SimpleDateFormat mLogFileFormat = new SimpleDateFormat(FORMAT + SPLIT + "HH:mm:ss");
    private static SimpleDateFormat mLogPreFormat = new SimpleDateFormat(FORMAT);

    public static void initXLog() {
        LogConfiguration config = new LogConfiguration.Builder()
                .logLevel( LogLevel.ALL)
                .build();

        Printer androidPrinter = new AndroidPrinter();
        Printer filePrinter = new FilePrinter
                .Builder(LOG_LOCATION)
                .fileNameGenerator(new ChangelessFileNameGenerator(mLogFileFormat.format(System.currentTimeMillis()) + ".log"))
                .backupStrategy(new NeverBackupStrategy())
                .logFlattener(new ClassicFlattener())
                .build();

        XLog.init(config, androidPrinter, filePrinter);

        deleteHistoryLog();
    }


    public static void v(String tag,String log){
        XLog.tag(tag).v(log);
    }

    public static void i(String tag,String log){
        XLog.tag(tag).i(log);
    }

    public static void d(String tag,String log){
        XLog.tag(tag).d(log);
    }

    public static void w(String tag,String log){
        XLog.tag(tag).w(log);
    }

    public static void e(String tag,String log){
        XLog.tag(tag).e(log);
    }

    private static int deleteHistoryLog() {
        File logFileDir = new File(LOG_LOCATION);
        String[] files = logFileDir.list();
        if (files == null || files.length < 10) {
            //超过10个才按日期删除
            return 0;
        }

        final Date todayDate = Calendar.getInstance().getTime();
        File[] logFiles = logFileDir.listFiles(new FilenameFilter() {
            @Override
            public boolean accept(File file, String filename) {
                // select the file to be delete.
                if (!TextUtils.isEmpty(filename)) {
                    String dataSplit[] = filename.split(SPLIT);
                    if (dataSplit != null && dataSplit.length > 0) {
                        try {
                            Date fileDate = mLogPreFormat.parse(dataSplit[0]);
                            long offset = (todayDate.getTime() - fileDate.getTime()) / (1000 * 60 * 60 * 24);
                            if (offset > 5) {
                                return true; //delete.
                            }
                        } catch (ParseException e) {
                            e.printStackTrace();
                        }
                    }
                    return false;
                } else {
                    return false;
                }
            }
        });

        int deleted = 0;
        if (logFiles != null) {
            for (File log : logFiles) {
                if (log.delete()) {
                    deleted++;
                }
            }
        }

        return deleted;
    }
}
