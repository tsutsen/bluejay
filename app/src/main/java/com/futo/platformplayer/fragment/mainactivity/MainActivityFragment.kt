package com.futo.platformplayer.fragment.mainactivity

import android.util.Log
import androidx.fragment.app.Fragment
import com.futo.platformplayer.activities.MainActivity
import com.futo.platformplayer.fragment.mainactivity.main.MainFragment

open class MainActivityFragment : Fragment() {
    protected val currentMain : MainFragment?
        get() {
        isValidMainActivity();
        return when (activity) {
            is MainActivity -> (activity as MainActivity).fragCurrent
            is androidx.fragment.app.FragmentActivity -> {
                // In PlatformPlayerActivity, get current fragment from supportFragmentManager
                val fragmentManager = (activity as androidx.fragment.app.FragmentActivity).supportFragmentManager
                fragmentManager.primaryNavigationFragment as? MainFragment
            }
            else -> null
        }
    }

    fun closeSegment() {
        val a = activity
        if (a is MainActivity)
            return a.closeSegment()
        else
            Log.d(TAG, "closeSegment not supported in PlatformPlayerActivity")
    }

    fun navigate(frag: MainFragment, parameter: Any? = null, withHistory: Boolean = true) {
        val a = activity
        if (a is MainActivity)
            (activity as MainActivity).navigate(frag, parameter, withHistory, false)
        else
            Log.d(TAG, "navigate not supported in PlatformPlayerActivity")
    }

    inline fun <reified T : MainFragment> navigate(parameter: Any? = null, withHistory: Boolean = true): T {
        val target = requireFragment<T>();
        navigate(target, parameter, withHistory);
        return target;
    }

    fun navigateTab(frag: MainFragment, parameter: Any? = null) {
        val a = activity
        if (a is MainActivity)
            a.navigateTab(frag, parameter)
        else
            Log.d(TAG, "navigateTab not supported in PlatformPlayerActivity")
    }

    inline fun <reified T : MainFragment> navigateTab(parameter: Any? = null): T {
        val target = requireFragment<T>();
        navigateTab(target, parameter);
        return target;
    }

    inline fun <reified T : Fragment> requireFragment() : T {
        isValidMainActivity();
        return when (activity) {
            is MainActivity -> (activity as MainActivity).getFragment<T>()
            is androidx.fragment.app.FragmentActivity -> {
                val fragmentManager = (activity as androidx.fragment.app.FragmentActivity).supportFragmentManager
                fragmentManager.findFragmentByTag(T::class.java.simpleName) as? T
                    ?: throw java.lang.IllegalStateException("Fragment ${T::class.java.simpleName} not found")
            }
            else -> throw java.lang.IllegalStateException("Invalid activity")
        }
    }

    fun isValidMainActivity(){
        if(activity == null)
            throw java.lang.IllegalStateException("Attempted to use fragment without an activity");
        // Allow both old MainActivity and new PlatformPlayerActivity during migration
        if(!(activity is MainActivity || activity is androidx.fragment.app.FragmentActivity))
            throw java.lang.IllegalStateException("Attempted to use fragment without a valid activity");
    }

    companion object {
        private const val TAG = "MainActivityFragment"
    }
}