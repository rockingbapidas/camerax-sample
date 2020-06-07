package com.bapidas.camerax.ui.preview

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.MediaController
import androidx.databinding.DataBindingUtil
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.navArgs
import com.bapidas.camerax.BR
import com.bapidas.camerax.R
import com.bapidas.camerax.databinding.PreviewFragmentBinding
import kotlinx.android.synthetic.main.preview_fragment.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

class PreviewFragment : Fragment(),
    PreviewNavigator {
    private val mViewModel: PreviewViewModel by lazy {
        ViewModelProvider(this).get(PreviewViewModel::class.java)
    }
    private val args: PreviewFragmentArgs by navArgs()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        mViewModel.setArguments(args.mediaData)
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        val binding = DataBindingUtil.inflate<PreviewFragmentBinding>(
            inflater, R.layout.preview_fragment, container, false
        ).apply {
            setVariable(BR.viewModel, this@PreviewFragment.mViewModel)
            setVariable(BR.callback, this@PreviewFragment)
        }.also {
            it.lifecycleOwner = this
        }
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        if (mViewModel.isVideo()) {
            val mediaController = MediaController(requireContext())
            video_view.setMediaController(mediaController)
        }
    }
}