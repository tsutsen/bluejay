package com.futo.platformplayer.compose.interop

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.ComposeView
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModel

/**
 * Base Fragment for hosting Compose content within the existing Fragment navigation graph.
 *
 * This is the standard interop convention for the Compose migration:
 * - Compose screens are hosted via a Fragment whose onCreateView returns a ComposeView.
 * - This fits into the existing FragmentContainerView-based navigation unchanged.
 * - Legacy custom views embedded inside a new Compose screen use AndroidView.
 *
 * Usage:
 *   class MyScreenFragment : ComposeFragment() {
 *       override fun createContent() = @Composable { MyScreen() }
 *   }
 *
 * For screens that need a ViewModel, call viewModel() from within the composable:
 *   override fun createContent() = @Composable {
 *       val viewModel: MyViewModel = androidx.lifecycle.viewmodel.compose.viewModel()
 *       MyScreen(viewModel)
 *   }
 */
abstract class ComposeFragment : Fragment() {

    private var _composeView: ComposeView? = null

    /**
     * Create the Compose content for this fragment.
     * ViewModels should be obtained inside the composable via viewModel<T>().
     */
    protected abstract fun createContent(): @Composable () -> Unit

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        val composeView = ComposeView(requireContext()).apply {
            setId(android.R.id.content)
            setContent {
                createContent().invoke()
            }
        }
        _composeView = composeView
        return composeView
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _composeView = null
    }
}
