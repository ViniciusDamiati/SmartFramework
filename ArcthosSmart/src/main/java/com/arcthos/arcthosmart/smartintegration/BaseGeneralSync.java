package com.arcthos.arcthosmart.smartintegration;

import android.content.Context;
import android.content.pm.ApplicationInfo;
import android.os.AsyncTask;
import android.util.Log;

import com.arcthos.arcthosmart.annotations.SObject;
import com.arcthos.arcthosmart.annotations.SoqlWhere;
import com.arcthos.arcthosmart.smartorm.SmartObject;
import com.salesforce.androidsdk.smartsync.util.Constants;

import java.io.IOException;
import java.lang.reflect.Field;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Arrays;
import java.util.Date;
import java.util.Enumeration;
import java.util.List;
import java.util.Locale;
import java.util.TimeZone;

import dalvik.system.DexFile;

import static android.os.AsyncTask.THREAD_POOL_EXECUTOR;

/**
 * Created by Vinicius Damiati on 16-Oct-17.
 */

public abstract class BaseGeneralSync {

    public static final String LAST_SYNC = "lastSync";

    private Context context;
    private SyncCallback syncCallback;
    private String formattedLastUpdate;

    public BaseGeneralSync(Context context, SyncCallback syncCallback) {
        this.context = context;
        this.syncCallback = syncCallback;
        PreferencesManager.initialize(context);
    }

    private String formatLastUpdate(String lastUpdate) {
        Locale locale = context.getResources().getConfiguration().locale;
        SimpleDateFormat sdf = new SimpleDateFormat("dd-MM-yyyy HH:mm:ss");
        SimpleDateFormat sdf2 = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", locale);

        sdf.setTimeZone(TimeZone.getTimeZone("UTC"));
        sdf2.setTimeZone(TimeZone.getTimeZone("UTC"));

        Date lastUpdateDate;
        try {
            lastUpdateDate = sdf.parse(lastUpdate);
        } catch (ParseException e) {
            lastUpdateDate = new Date(0);
        }

        return sdf2.format(lastUpdateDate);
    }

    public synchronized void performSync() {
        String lastUpdate = PreferencesManager.getInstance().getStringValue(LAST_SYNC);
        formattedLastUpdate = formatLastUpdate(lastUpdate);

        syncObjects();
        new AsyncTask<Void, Void, Void>() {
            @Override
            protected Void doInBackground(Void... voids) {
                syncChainedObjects();
                return null;
            }
        }.executeOnExecutor(THREAD_POOL_EXECUTOR);
        setLastDateUpdate();
    }

    protected abstract void syncObjects();

    protected abstract void syncChainedObjects();

    private void setLastDateUpdate() {
        SimpleDateFormat sdf = new SimpleDateFormat("dd-MM-yyyy HH:mm:ss");
        sdf.setTimeZone(TimeZone.getTimeZone("UTC"));

        PreferencesManager.getInstance().setValue(LAST_SYNC, sdf.format(new Date()));
    }

    protected void syncObject(Class<? extends SmartObject> model) {
        SObjectSyncher sObjectSyncher = new SObjectSyncher(model, context, syncCallback, false);
        String where = Constants.LAST_MODIFIED_DATE + ">" + formattedLastUpdate + " " + getCustomWhere(model);
        sObjectSyncher.setWhere(where);

        new SyncObjectTask(sObjectSyncher).executeOnExecutor(THREAD_POOL_EXECUTOR);
    }

    protected void syncChainedObject(Class<? extends SmartObject> model) {
        SObjectSyncher sObjectSyncher = new SObjectSyncher(model, context, syncCallback, true);
        String where = Constants.LAST_MODIFIED_DATE + ">" + formattedLastUpdate + " " + getCustomWhere(model);
        sObjectSyncher.setWhere(where);

        if(sObjectSyncher.hasSoup()) {
            sObjectSyncher.syncUpAndDown();
        } else {
            sObjectSyncher.syncDown();
        }
    }

    private String getCustomWhere(Class<? extends SmartObject> model) {
        String where = "";

        List<Field> fields = Arrays.asList(model.getDeclaredFields());
        for(Field field : fields) {
            if(field.isAnnotationPresent(SoqlWhere.class)) {
                //TODO: validate if starts with an AND or OR than put or not the AND word
                where = invoke(field.getName(), model);
            }
        }

        return where;
    }

    private String invoke(String field, Class<? extends SmartObject> model) {
        try {
            return (String) model.getField(field).get(null);
        } catch (Exception e) {
            return "";
        }
    }

    public String getFormattedLastUpdate() {
        return formattedLastUpdate;
    }

    public int getAmountSObjectClasses() {
        int sObjectsAmount = 0;

        ApplicationInfo applicationInfo = context.getApplicationInfo();
        String classPath = applicationInfo.sourceDir;

        DexFile dexFile = null;
        try {
            dexFile = new DexFile(classPath);

            Enumeration<String> iter = dexFile.entries();
            while (iter.hasMoreElements()) {
                String classCanonicalName = iter.nextElement();
                Class clazz = null;
                try {
                    clazz = context.getClassLoader().loadClass(classCanonicalName);
                    if(clazz.isAnnotationPresent(SObject.class)) {
                        sObjectsAmount++;
                    }
                } catch (ClassNotFoundException e) {
                    continue;
                }
            }

            return sObjectsAmount;
        } catch (IOException e) {
            Log.e(BaseGeneralSync.class.getSimpleName(), e.getMessage(), e);
            return -1;
        } finally {
            try {
                dexFile.close();
            } catch (IOException e) {
                Log.e(BaseGeneralSync.class.getSimpleName(), e.getMessage(), e);
            }
        }
    }

}