package com.dakingx.photopicker.fragment

import android.Manifest
import android.app.Activity
import android.content.ContentResolver
import android.content.ContentValues
import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.provider.MediaStore
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.FileProvider
import androidx.core.net.toFile
import com.dakingx.photopicker.ext.checkAppPermission
import com.dakingx.photopicker.ext.generateTempFile
import com.dakingx.photopicker.ext.generateTempFile2
import kotlin.random.Random

sealed class PhotoOpResult {
    class Success(val uri: Uri) : PhotoOpResult()
    object Failure : PhotoOpResult()
    object Cancel : PhotoOpResult()
}

typealias PhotoOpCallback = (PhotoOpResult) -> Unit

class PhotoFragment : BaseFragment() {
    companion object {
        const val FRAGMENT_TAG = "photo_fragment"

        val REQUIRED_PERMISSIONS_FOR_PICK_TIRAMISU = listOf(
            Manifest.permission.READ_MEDIA_IMAGES
        )

        val REQUIRED_PERMISSIONS_FOR_PICK =
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                listOf(
                    Manifest.permission.READ_MEDIA_IMAGES
                )
            } else {
                listOf(
                    Manifest.permission.READ_EXTERNAL_STORAGE
                )
            }

        val REQUIRED_PERMISSIONS_FOR_CROP = REQUIRED_PERMISSIONS_FOR_PICK

        val REQUIRED_PERMISSIONS_FOR_CAPTURE = listOf(
            Manifest.permission.CAMERA
        ) + REQUIRED_PERMISSIONS_FOR_PICK

        @JvmStatic
        fun newInstance(fileProviderAuthority: String, delPhoto: Boolean = true) =
            PhotoFragment().apply {
                arguments = Bundle().apply {
                    putString(ARG_FILE_PROVIDER_AUTH, fileProviderAuthority)
                    putBoolean(ARG_DEL_PHOTO, delPhoto)
                }
            }

        private const val ARG_FILE_PROVIDER_AUTH = "arg_file_provider_auth"
        private const val ARG_DEL_PHOTO = "arg_del_photo"
    }

    private var fileProviderAuthority: String = ""

    private var captureFileUri: Uri? = null
    var delPhoto = true //删除拍照、截图的原图
    private var cropFileUri: Uri? = null

    private var captureCallback: PhotoOpCallback? = null
    private var pickCallback: PhotoOpCallback? = null
    private var cropCallback: PhotoOpCallback? = null

    //拍照触发
    private val captureLauncher =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) {
            when (it.resultCode) {
                Activity.RESULT_OK -> {
                    val uri = captureFileUri

                    captureCallback?.invoke(
                        if (uri != null) PhotoOpResult.Success(uri)
                        else PhotoOpResult.Failure
                    )
                }

                Activity.RESULT_CANCELED -> {
                    captureCallback?.invoke(PhotoOpResult.Cancel)
                }

                else -> {
                    captureCallback?.invoke(PhotoOpResult.Failure)
                }
            }

            captureCallback = null
        }

    //图片触发
    private val picLauncher =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) {
            when (it.resultCode) {
                Activity.RESULT_OK -> {
                    val uri = it.data?.data

                    pickCallback?.invoke(
                        if (uri != null) PhotoOpResult.Success(uri)
                        else PhotoOpResult.Failure
                    )
                }

                Activity.RESULT_CANCELED -> {
                    pickCallback?.invoke(PhotoOpResult.Cancel)
                }

                else -> {
                    pickCallback?.invoke(PhotoOpResult.Failure)
                }
            }
            pickCallback = null
        }

    private val cropLauncher =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) {
            when (it.resultCode) {
                Activity.RESULT_OK -> {
                    val uri = cropFileUri
                    cropCallback?.invoke(
                        if (uri != null) PhotoOpResult.Success(uri)
                        else PhotoOpResult.Failure
                    )
                }

                Activity.RESULT_CANCELED -> {
                    cropCallback?.invoke(PhotoOpResult.Cancel)
                }

                else -> {
                    cropCallback?.invoke(PhotoOpResult.Failure)
                }
            }

            if (delPhoto) { //删除原图
                requireContext().contentResolver.delete(captureFileUri!!, null, null)
            }
            captureFileUri = null

            cropCallback = null
            cropFileUri = null
        }

    override fun restoreState(bundle: Bundle?) {
        bundle?.apply {
            getString(ARG_FILE_PROVIDER_AUTH)?.let {
                fileProviderAuthority = it
            }
            getBoolean(ARG_DEL_PHOTO).let {
                delPhoto = it
            }
        }
    }

    override fun storeState(bundle: Bundle) {
        bundle.also {
            it.putString(ARG_FILE_PROVIDER_AUTH, fileProviderAuthority)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        retainInstance = true
        if (fileProviderAuthority.isEmpty()) {
            throw RuntimeException("fileProviderAuthority can't be empty")
        }
    }

    override fun onDestroy() {
        captureCallback?.invoke(PhotoOpResult.Cancel)
        captureCallback = null
        pickCallback?.invoke(PhotoOpResult.Cancel)
        pickCallback = null
        cropCallback?.invoke(PhotoOpResult.Cancel)
        cropCallback = null

        super.onDestroy()
    }

    fun capture(callback: PhotoOpCallback) {
        if (!checkRequiredPermissions(REQUIRED_PERMISSIONS_FOR_CAPTURE)) { //应用权限检查
            callback.invoke(PhotoOpResult.Failure)
            return
        }

        this.captureCallback = callback

        val tempFile = context?.generateTempFile("capture_photo")
        if (tempFile == null) {
            callback.invoke(PhotoOpResult.Failure)
            return
        }

        captureFileUri = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            FileProvider.getUriForFile(requireContext(), fileProviderAuthority, tempFile)
        } else {
            Uri.fromFile(tempFile)
        }

        val intent = Intent(MediaStore.ACTION_IMAGE_CAPTURE).apply {
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION)
            putExtra(MediaStore.EXTRA_VIDEO_QUALITY, 1)
            putExtra(MediaStore.EXTRA_OUTPUT, captureFileUri)
        }
        captureLauncher.launch(intent)
    }

    fun pick(callback: PhotoOpCallback) {
        if (!checkRequiredPermissions(REQUIRED_PERMISSIONS_FOR_PICK)) { //应用权限检查
            callback.invoke(PhotoOpResult.Failure)
            return
        }

        this.pickCallback = callback

        val intent =
            Intent(Intent.ACTION_PICK, MediaStore.Images.Media.EXTERNAL_CONTENT_URI).apply {
                type = "image/*"
            }
        picLauncher.launch(intent)
    }

    /**
     * @param fromCamera true:拍照；false:文件选图。
     */
    fun crop(uri: Uri, fromCamera: Boolean = true, callback: PhotoOpCallback) {
        if (!checkRequiredPermissions(REQUIRED_PERMISSIONS_FOR_CROP)) { //应用权限检查
            callback.invoke(PhotoOpResult.Failure)
            return
        }
        this.cropCallback = callback

        val sourceUri =
            if (uri.scheme.equals(ContentResolver.SCHEME_FILE)) FileProvider.getUriForFile(
                requireContext(), fileProviderAuthority, uri.toFile()
            )
            else uri

        val mimeType = requireContext().contentResolver.getType(sourceUri)
        val fileName = "crop_photo_${System.currentTimeMillis()}_${Random.nextInt(9999)}.jpg"
        val destinationUri = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val values = ContentValues()
            values.put(MediaStore.MediaColumns.DISPLAY_NAME, fileName)
            values.put(MediaStore.MediaColumns.MIME_TYPE, mimeType)
            values.put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_DCIM)
            requireContext().contentResolver.insert(
                MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values
            )
        } else {
            val file = context?.generateTempFile2(fileName)
            if (file == null) {
                callback.invoke(PhotoOpResult.Failure)
                return
            }
            Uri.fromFile(file)
        }

        if (destinationUri == null) {
            callback.invoke(PhotoOpResult.Failure)
            return
        }
        cropFileUri = destinationUri

        val cropIntent = Intent("com.android.camera.action.CROP").apply {
            if (fromCamera) {
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION)
            }

            setDataAndType(sourceUri, mimeType)
            putExtra("noFaceDetection", true)
            putExtra("crop", "true")
            putExtra("scale", true)
            putExtra("scaleUpIfNeeded", true)
            putExtra(MediaStore.EXTRA_OUTPUT, destinationUri)
            putExtra("outputFormat", Bitmap.CompressFormat.JPEG.name)
            putExtra("return-data", false)
        }
        cropLauncher.launch(cropIntent)
    }

    private fun checkRequiredPermissions(requiredPermissions: List<String>): Boolean =
        context?.checkAppPermission(*requiredPermissions.toTypedArray()) ?: false
}