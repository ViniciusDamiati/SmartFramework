/*
 * Copyright (c) 2014-present, salesforce.com, inc.
 * All rights reserved.
 * Redistribution and use of this software in source and binary forms, with or
 * without modification, are permitted provided that the following conditions
 * are met:
 * - Redistributions of source code must retain the above copyright notice, this
 * list of conditions and the following disclaimer.
 * - Redistributions in binary form must reproduce the above copyright notice,
 * this list of conditions and the following disclaimer in the documentation
 * and/or other materials provided with the distribution.
 * - Neither the name of salesforce.com, inc. nor the names of its contributors
 * may be used to endorse or promote products derived from this software without
 * specific prior written permission of salesforce.com, inc.
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS"
 * AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE
 * IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE
 * ARE DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT OWNER OR CONTRIBUTORS BE
 * LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR
 * CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF
 * SUBSTITUTE GOODS OR SERVICES; LOSS OF USE, DATA, OR PROFITS; OR BUSINESS
 * INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF LIABILITY, WHETHER IN
 * CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE)
 * ARISING IN ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN IF ADVISED OF THE
 * POSSIBILITY OF SUCH DAMAGE.
 */
package com.salesforce.androidsdk.mobilesync.util;

import com.salesforce.androidsdk.mobilesync.util.SyncState.MergeMode;
import com.salesforce.androidsdk.util.JSONObjectHelper;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.List;

/**
 * Options for sync up / down
 */
public class SyncOptions {

	public static final String MERGEMODE = "mergeMode";

	// Fieldlist really belongs in sync up/down target - keeping it here for backwards compatibility
	public static final String FIELDLIST = "fieldlist";
	public static final String FIELDLISTFORUPDATE = "fieldlistforupdate";

	private MergeMode mergeMode;
	private List<String> fieldlist;
	private List<String> fieldlistForUpdate;

	/**
	 * Build SyncOptions from json
	 * @param options as json
	 * @return
	 * @throws JSONException
	 */
	public static SyncOptions fromJSON(JSONObject options) throws JSONException {
		if (options == null)
			return null;

		String mergeModeStr = JSONObjectHelper.optString(options, MERGEMODE);
		MergeMode mergeMode = mergeModeStr == null ? null : MergeMode.valueOf(mergeModeStr);
		List<String> fieldlist = JSONObjectHelper.toList(options.optJSONArray(FIELDLIST));
		List<String> fieldlistforupdate;

		try {
			fieldlistforupdate = JSONObjectHelper.toList(options.optJSONArray(FIELDLISTFORUPDATE));
			return new SyncOptions(fieldlist, fieldlistforupdate, mergeMode);
		} catch (Exception e) {
			return new SyncOptions(fieldlist, mergeMode);
		}
	}

	/**
	 * @param fieldlist
	 * @return
	 */
	public static SyncOptions optionsForSyncUp(List<String> fieldlist) {
		return new SyncOptions(fieldlist, MergeMode.OVERWRITE);
	}

	/**
	 * @param fieldlist
	 * @param mergeMode
	 * @return
	 */
	public static SyncOptions optionsForSyncUp(List<String> fieldlist, MergeMode mergeMode) {
		return new SyncOptions(fieldlist, mergeMode);
	}

	/**
	 * @param fieldlist
	 * @param fieldlistForUpdate
	 * @param mergeMode
	 * @return
	 */
	public static SyncOptions optionsForSyncUp(List<String> fieldlist, List<String> fieldlistForUpdate, MergeMode mergeMode) {
		return new SyncOptions(fieldlist, fieldlistForUpdate, mergeMode);
	}

	/**
	 * @param mergeMode
	 * @return
	 */
	public static SyncOptions optionsForSyncDown(MergeMode mergeMode) {
		return new SyncOptions(null, mergeMode);
	}

	/**
	 * Private constructor
	 * @param fieldlist
	 * @param mergeMode
	 */
	private SyncOptions(List<String> fieldlist, MergeMode mergeMode) {
		this.fieldlist = fieldlist;
		this.mergeMode = mergeMode;
	}

	/**
	 * Private constructor
	 * @param fieldlist
	 * @param mergeMode
	 */
	private SyncOptions(List<String> fieldlist, List<String> fieldlistForUpdate, MergeMode mergeMode) {
		this.fieldlist = fieldlist;
		this.mergeMode = mergeMode;
		this.fieldlistForUpdate = fieldlistForUpdate;
	}

	/**
	 * @return json representation of target
	 * @throws JSONException
	 */
	public JSONObject asJSON() throws JSONException {
		JSONObject options = new JSONObject();
		if (mergeMode != null) options.put(MERGEMODE, mergeMode.name());
		if (fieldlist != null) options.put(FIELDLIST, new JSONArray(fieldlist));
		if (fieldlistForUpdate != null) options.put(FIELDLISTFORUPDATE, new JSONArray(fieldlistForUpdate));
		return options;
	}

	public List<String> getFieldlist() {
		return fieldlist;
	}

	public List<String> getFieldlistForUpdate() {
		return fieldlistForUpdate;
	}

	public MergeMode getMergeMode() {
		return mergeMode;
	}

}
