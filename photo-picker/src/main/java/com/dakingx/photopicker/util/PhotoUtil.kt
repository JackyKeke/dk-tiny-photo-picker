package com.dakingx.photopicker.util

import android.net.Uri
import androidx.fragment.app.FragmentManager
import com.dakingx.photopicker.ext.resumeSafely
import com.dakingx.photopicker.fragment.PhotoFragment
import com.dakingx.photopicker.fragment.PhotoOpCallback
import com.dakingx.photopicker.fragment.PhotoOpResult
import kotlinx.coroutines.CancellableContinuation
import kotlinx.coroutines.suspendCancellableCoroutine

/**
 * 拍照并裁剪
 * @param authority fileProvider
 * @param delPhoto 删除拍照原图、截图的原图
 */
suspend fun capturePhoto(
    fm: FragmentManager, authority: String, delPhoto: Boolean = true
): PhotoOpResult = suspendCancellableCoroutine { continuation ->
    val fragment = getPhotoFragment(fm, authority, delPhoto)
    fragment.capture(genCropPhotoCb(fragment, true, continuation))
}

/**
 * 选取照片并裁剪
 */
suspend fun pickPhoto(fm: FragmentManager, authority: String): PhotoOpResult =
    suspendCancellableCoroutine { continuation ->
        val fragment = getPhotoFragment(fm, authority, false)
        fragment.pick(genCropPhotoCb(fragment, false, continuation))
    }

/**
 * 处理裁剪
 * @param fromCamera true:拍照；false:文件选图。
 */
private fun genCropPhotoCb(
    fragment: PhotoFragment,
    fromCamera: Boolean,
    continuation: CancellableContinuation<PhotoOpResult>
) = object : PhotoOpCallback {
    override fun invoke(result: PhotoOpResult) {
        when (result) {
            is PhotoOpResult.Success -> { //裁剪
                fragment.crop(result.uri, fromCamera) { cropResult ->
                    continuation.resumeSafely(cropResult)
                }
            }

            else -> {
                continuation.resumeSafely(result)
            }
        }
    }
}

/**
 * 裁剪
 */
suspend fun cropPhoto(
    fm: FragmentManager,
    authority: String,
    sourceUri: Uri,
): PhotoOpResult = suspendCancellableCoroutine { continuation ->
    val fragment = getPhotoFragment(fm, authority, false)
    fragment.crop(sourceUri) { cropResult ->
        continuation.resumeSafely(cropResult)
    }
}

/**
 * 获取PhotoFragment
 */
private fun getPhotoFragment(
    fm: FragmentManager, fileProviderAuthority: String, delPhoto: Boolean = true
) = (fm.findFragmentByTag(PhotoFragment.FRAGMENT_TAG) as? PhotoFragment)?.apply {
    this.delPhoto = delPhoto
} ?: PhotoFragment.newInstance(fileProviderAuthority, delPhoto).apply {
    fm.beginTransaction().add(this, PhotoFragment.FRAGMENT_TAG).commitAllowingStateLoss()
    fm.executePendingTransactions()
}