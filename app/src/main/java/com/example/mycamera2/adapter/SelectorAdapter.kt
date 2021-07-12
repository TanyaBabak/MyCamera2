package com.example.mycamera.adapter

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.example.mycamera.data.CameraInfo
import com.example.mycamera2.databinding.ItemSelectorCameraBinding

class SelectorAdapter(
    val cameras: List<CameraInfo>,
    val callBack: (cameraInfo: CameraInfo) -> Unit
) :
    RecyclerView.Adapter<SelectorAdapter.SelectorHolder>() {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): SelectorHolder {
        val binding =
            ItemSelectorCameraBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return SelectorHolder(binding)
    }

    override fun onBindViewHolder(holder: SelectorHolder, position: Int) {
        holder.binding.tvNameCamera.text = cameras[position].name
        holder.binding.clRoot.setOnClickListener {
            callBack.invoke(cameras[position])
        }
    }

    override fun getItemCount() = cameras.size

    inner class SelectorHolder(val binding: ItemSelectorCameraBinding) :
        RecyclerView.ViewHolder(binding.root)
}