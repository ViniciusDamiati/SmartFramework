package com.arcthos.smartframework.smartintegration;

import android.content.Context;
import android.util.Log;

import com.arcthos.smartframework.annotations.DestinationLocalParent;
import com.arcthos.smartframework.annotations.SObject;
import com.arcthos.smartframework.annotations.SourceLocalParent;
import com.arcthos.smartframework.smartintegration.helpers.ModelBuildingHelper;
import com.arcthos.smartframework.smartorm.Condition;
import com.arcthos.smartframework.smartorm.SmartObject;
import com.arcthos.smartframework.smartorm.SmartObjectConstants;
import com.arcthos.smartframework.smartorm.SmartSelect;
import com.arcthos.smartframework.smartorm.repository.Repository;
import com.google.gson.annotations.SerializedName;
import com.salesforce.androidsdk.accounts.UserAccount;
import com.salesforce.androidsdk.smartstore.store.SmartStore;
import com.salesforce.androidsdk.smartsync.app.SmartSyncSDKManager;
import com.salesforce.androidsdk.smartsync.manager.SyncManager;
import com.salesforce.androidsdk.smartsync.target.SoqlSyncDownTarget;
import com.salesforce.androidsdk.smartsync.target.SyncDownTarget;
import com.salesforce.androidsdk.smartsync.target.SyncUpTarget;
import com.salesforce.androidsdk.smartsync.util.Constants;
import com.salesforce.androidsdk.smartsync.util.SOQLBuilder;
import com.salesforce.androidsdk.smartsync.util.SyncOptions;
import com.salesforce.androidsdk.smartsync.util.SyncState;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.lang.annotation.Annotation;
import java.lang.reflect.Field;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TimeZone;

/**
 * Created by Vinicius Damiati on 11-Oct-17.
 */

public class SObjectSyncher<T extends SmartObject>{

    public static final Integer LIMIT = 50000;

    private final UserAccount currentUser;
    private final SmartStore smartStore;
    private final SyncManager syncMgr;
    private final Class<T> type;
    private final ModelBuildingHelper modelBuildingHelper;
    private String where;
    private long syncId = -1;
    private final SyncCallback syncCallback;
    private final Context context;
    private final boolean chainedSync;

    public SObjectSyncher(final Class<T> type, final Context context, final SyncCallback syncCallback, boolean chainedSync) {
        this.currentUser = SmartSyncSDKManager.getInstance().getUserAccountManager().getCurrentUser();
        this.smartStore = SmartSyncSDKManager.getInstance().getSmartStore(currentUser);
        this.syncMgr = SyncManager.getInstance(currentUser);
        this.type = type;
        this.syncCallback = syncCallback;
        this.context = context;
        this.chainedSync = chainedSync;
        this.modelBuildingHelper = new ModelBuildingHelper(type);
        this.where = getDefaultWhere();
    }

    private String getDefaultWhere() {
        Locale locale = context.getResources().getConfiguration().locale;
        SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", locale);
        sdf.setTimeZone(TimeZone.getTimeZone("UTC"));

        String now = sdf.format(new Date());

        String where = Constants.LAST_MODIFIED_DATE + ">" + now + " ";

        return where;
    }

    public String getWhere() {
        return where;
    }

    public void setWhere(String where) {
        this.where = where;
    }

    public boolean hasSoup() {
        if(smartStore.hasSoup(modelBuildingHelper.getSObjectName())) {
            return true;
        } else {
            return false;
        }
    }

    public synchronized void syncUp() {
        syncUp(false, null);
    }

    public synchronized void syncUpAndDown() {
        syncUp(true, null);
    }

    private synchronized void syncUp(final boolean doSyncdownAfter) {
        syncUp(doSyncdownAfter, null);
    }

    private synchronized void syncUp(final boolean doSyncdownAfter, final String id) {
        List<String> fieldsSyncUp = modelBuildingHelper.getFieldsToSyncUp();
        if(chainedSync) {
            try {
                updateChainedObject();
            } catch (JSONException e) {
                Log.e(type.getSimpleName(), "SmartSyncException occurred while attempting to update for chain sync", e);
            }
        }

        SyncUpTarget target;
        JSONObject object = null;
        if (id == null) {
            target = new SyncUpTarget();
        } else {
            object = SmartSelect.from(smartStore, type)
                    .where(Condition.prop(Constants.ID).eq(id))
                    .rawFirst();
            try {
                target = new SyncUpTarget(object);
            } catch (JSONException e) {
                target = new SyncUpTarget();
            }
        }

        final SyncOptions options = SyncOptions.optionsForSyncUp(fieldsSyncUp, SyncState.MergeMode.OVERWRITE);

        try {
            final JSONObject finalObject = object;
            syncMgr.syncUp(target, options, modelBuildingHelper.getSObjectName(), new SyncManager.SyncUpdateCallback() {
                @Override
                public void onUpdate(SyncState sync) {
                    if (SyncState.Status.DONE.equals(sync.getStatus()) || SyncState.Status.FAILED.equals(sync.getStatus())) {
                        try {
                            if (SyncState.Status.DONE.equals(sync.getStatus()) && doSyncdownAfter) {
                                syncCallback.onUpSuccess(sync, sync.getStatus(), sync.getSoupName());

                                if (id != null) {
                                    where = Constants.ID + "='" + id + "'";
                                }

                                syncDown();
                            } else if(SyncState.Status.DONE.equals(sync.getStatus())) {
                                syncCallback.onUpSuccess(sync, sync.getStatus(), sync.getSoupName());
                            } else if (SyncState.Status.FAILED.equals(sync.getStatus())) {
                                syncCallback.onUpFailure(sync, finalObject);
                            }
                        } catch (Exception e) {
                            Log.e(type.getSimpleName(), e.getMessage(), e);
                        }
                    }
                }
            });
        } catch (JSONException e) {
            Log.e(type.getSimpleName(), "JSONException occurred while parsing", e);
        } catch (SyncManager.SmartSyncException e) {
            Log.e(type.getSimpleName(), "SmartSyncException occurred while attempting to sync up", e);
        } catch (Exception e) {
            Log.e(type.getSimpleName(), "Exception occurred while attempting to sync up", e);
        }
    }

    public synchronized void syncDown() {
        String sObjectName = modelBuildingHelper.getSObjectName();
        smartStore.registerSoup(sObjectName, modelBuildingHelper.getIndexSpecs());
        final SyncManager.SyncUpdateCallback callback = new SyncManager.SyncUpdateCallback() {
            @Override
            public void onUpdate(SyncState sync) {
                if (SyncState.Status.DONE.equals(sync.getStatus())) {
                    syncCallback.onDownSuccess(sync, sync.getTotalSize(), sync.getSoupName());
                } else if(SyncState.Status.FAILED.equals(sync.getStatus())) {
                    syncCallback.onDownFailure(sync);
                }
            }
        };
        try {
            if (syncId == -1) {
                List<String> fieldsSyncDown = modelBuildingHelper.getFieldsToSyncDown();

                final SyncOptions options = SyncOptions.optionsForSyncDown(SyncState.MergeMode.OVERWRITE);
                final String soqlQuery = SOQLBuilder.
                        getInstanceWithFields(fieldsSyncDown)
                        .from(sObjectName)
                        .where(where)
                        .limit(LIMIT).build();
                Log.d(sObjectName + "::QUERY SOQL:", soqlQuery);
                final SyncDownTarget target = new SoqlSyncDownTarget(soqlQuery);

                try {
                    final SyncState sync = syncMgr.syncDown(target, options, sObjectName, callback);
                    syncId = sync.getId();
                } catch (Exception ex) {
                    Log.e(type.getSimpleName(), ex.getMessage(), ex);
                }
            } else {
                syncMgr.reSync(syncId, callback);
            }
        } catch (JSONException e) {
            Log.e(type.getSimpleName(), "JSONException occurred while parsing", e);
        } catch (SyncManager.SmartSyncException e) {
            Log.e(type.getSimpleName(), "SmartSyncException occurred while attempting to sync down", e);
        }
    }

    private void updateChainedObject() throws JSONException {
        JSONArray models = SmartSelect.from(smartStore, type).rawList();
        Map<String, Class<? extends SmartObject>> sourceClassBySourceFieldName = new HashMap<>();
        Map<String, String> destinationBySource = getDestinationBySource(sourceClassBySourceFieldName);

        for(int i = 0; i < models.length(); i++) {
            boolean hasToUpdate = false;
            JSONObject model = models.getJSONObject(i);

            for(String fieldName : destinationBySource.keySet()) {
                if(model.get(fieldName) != null) {
                    Repository repository = new Repository(smartStore, sourceClassBySourceFieldName.get(fieldName)) {};
                    String retrievedId = repository.findByEntryId(model.getLong(fieldName)).getId();
                    if(retrievedId.length() > 18) continue;
                    model.put(destinationBySource.get(fieldName), retrievedId);
                    model.put(fieldName, null);
                    hasToUpdate = true;
                }
            }

            if(hasToUpdate) {
                smartStore.update(getSoup(type), model, model.getLong(SmartObjectConstants.SOUP_ENTRY_ID));
            }
        }
    }

    private Map<String, String> getDestinationBySource (Map<String, Class<? extends SmartObject>> sourceClassBySourceFieldName) {
        List<Field> sourceFields = new ArrayList<>();
        List<Field> destinationFields = new ArrayList<>();
        List<Field> fields = Arrays.asList(type.getDeclaredFields());
        Map<String, String> destinationBySource = new HashMap<>();

        for(Field field : fields) {
            if (field.isAnnotationPresent(SourceLocalParent.class)) {
                sourceFields.add(field);
            }

            if(field.isAnnotationPresent(DestinationLocalParent.class)) {
                destinationFields.add(field);
            }
        }

        for(Field source : sourceFields) {
            Class<? extends SmartObject> sourceClass = null;
            for(Annotation annotation : source.getAnnotations()) {
                if(annotation instanceof SourceLocalParent){
                    sourceClass = ((SourceLocalParent)annotation).model();
                    break;
                }
            }

            for(Field destiantion : destinationFields) {
                Class<? extends SmartObject> destinationClass = null;
                String fieldName = "";
                for(Annotation annotation : source.getAnnotations()) {
                    if(annotation instanceof DestinationLocalParent){
                        destinationClass = ((DestinationLocalParent)annotation).model();
                    }

                    if(annotation instanceof SerializedName){
                        fieldName = ((SerializedName)annotation).value();
                    }
                }

                if(sourceClass != null && destinationClass != null && !fieldName.equals("") && sourceClass == destinationClass) {
                    destinationBySource.put(source.getName(), fieldName);
                    sourceClassBySourceFieldName.put(source.getName(), sourceClass);
                    break;
                }
            }
        }

        return destinationBySource;
    }

    private String getSoup(Class<? extends SmartObject> modelClass) {
        if(!modelClass.isAnnotationPresent(SObject.class)) {
            Log.e(SmartObject.class.getSimpleName() + "::GET_SOUP", "SObject annotation missing in model class: " + modelClass.getSimpleName());
            return "";
        }

        for(Annotation annotation : modelClass.getAnnotations()) {
            if(annotation instanceof SObject){
                return ((SObject)annotation).value();
            }
        }

        return "";
    }
}
