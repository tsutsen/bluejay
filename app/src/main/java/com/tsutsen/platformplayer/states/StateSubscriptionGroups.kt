package com.tsutsen.platformplayer.states

import com.tsutsen.platformplayer.constructs.Event0
import com.tsutsen.platformplayer.models.SubscriptionGroup
import com.tsutsen.platformplayer.stores.FragmentedStorage
import com.tsutsen.platformplayer.stores.StringDateMapStorage
import java.time.OffsetDateTime

/***
 * Used to maintain subscription groups
 */
class StateSubscriptionGroups {
    private val _subGroups = FragmentedStorage.storeJson<SubscriptionGroup>("subscription_groups")
        .withUnique { it.id }
        .load();


    private val _groupsRemoved = FragmentedStorage.get<StringDateMapStorage>("group_removed");

    val onGroupsChanged = Event0();

    fun getSubscriptionGroup(id: String): SubscriptionGroup? {
        return _subGroups.findItem { it.id == id };
    }
    fun getSubscriptionGroups(): List<SubscriptionGroup> {
        return _subGroups.getItems();
    }
    fun getSubscriptionGroupsRemovals(): Map<String, Long> {
        return _groupsRemoved.all();
    }
    fun updateSubscriptionGroup(subGroup: SubscriptionGroup, preventNotify: Boolean = false) {
        subGroup.lastChange = OffsetDateTime.now();
        _subGroups.save(subGroup);
        if(!preventNotify)
            onGroupsChanged.emit();
    }
    fun deleteSubscriptionGroup(id: String, isUserInteraction: Boolean = true){
        val group = getSubscriptionGroup(id);
        if(group != null) {
            _subGroups.delete(group);
            onGroupsChanged.emit();

            if(isUserInteraction) {
                _groupsRemoved.setAndSave(id, OffsetDateTime.now());
            }
        }
    }

    fun hasSubscriptionGroup(url: String): Boolean {
        val groups = getSubscriptionGroups();
        for(group in groups){
            if(group.urls.contains(url))
                return true;
        }
        return false;
    }


    companion object {
        const val TAG = "StateSubscriptionGroups";
        const val VERSION = 1;

        private var _instance : StateSubscriptionGroups? = null;
        val instance : StateSubscriptionGroups
            get(){
            if(_instance == null)
                _instance = StateSubscriptionGroups();
            return _instance!!;
        };

        fun finish() {
            _instance?.let {
                _instance = null;
            }
        }
    }
}