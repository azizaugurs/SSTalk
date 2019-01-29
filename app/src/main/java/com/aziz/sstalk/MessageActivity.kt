package com.aziz.sstalk

import android.Manifest
import android.animation.ArgbEvaluator
import android.animation.ObjectAnimator
import android.annotation.SuppressLint
import android.app.Activity
import android.content.*
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.Drawable
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.os.Environment.DIRECTORY_DCIM
import android.provider.MediaStore
import android.support.design.widget.Snackbar
import android.support.text.emoji.EmojiCompat
import android.support.text.emoji.bundled.BundledEmojiCompatConfig
import android.support.v4.app.ActivityCompat
import android.support.v4.content.ContextCompat
import android.support.v7.app.AlertDialog
import android.support.v7.app.AppCompatActivity
import android.support.v7.view.ActionMode
import android.support.v7.widget.LinearLayoutManager
import android.support.v7.widget.RecyclerView
import android.util.Log
import android.view.Menu
import android.view.MenuItem
import android.view.*
import android.view.ViewGroup
import android.support.v7.widget.SearchView
import android.widget.*
import com.aziz.sstalk.models.Models
import com.aziz.sstalk.utils.DateFormatter
import com.aziz.sstalk.utils.FirebaseUtils
import com.aziz.sstalk.utils.utils
import com.aziz.sstalk.views.holders
import com.firebase.ui.database.FirebaseRecyclerAdapter
import com.firebase.ui.database.FirebaseRecyclerOptions
import com.google.android.gms.maps.*
import com.google.android.gms.maps.model.BitmapDescriptorFactory
import com.google.android.gms.maps.model.LatLng
import com.google.android.gms.maps.model.MarkerOptions
import com.google.android.gms.tasks.Continuation
import com.google.android.gms.tasks.Task
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.ValueEventListener
import com.google.firebase.storage.FirebaseStorage
import com.google.firebase.storage.UploadTask
import com.mikhaellopez.circularprogressbar.CircularProgressBar
import com.squareup.picasso.Callback
import com.squareup.picasso.Picasso
import com.squareup.picasso.Target
import com.vincent.filepicker.Constant
import com.vincent.filepicker.activity.ImagePickActivity
import com.vincent.filepicker.activity.VideoPickActivity
import com.vincent.filepicker.filter.entity.ImageFile
import com.vincent.filepicker.filter.entity.VideoFile
import kotlinx.android.synthetic.main.activity_message.*
import me.shaohui.advancedluban.Luban
import me.shaohui.advancedluban.OnCompressListener
import java.io.File
import java.io.Serializable
import java.lang.Exception
import java.text.SimpleDateFormat
import java.util.*
import kotlin.collections.ArrayList
import kotlin.collections.HashMap

class MessageActivity : AppCompatActivity() {



    lateinit var mapRight:GoogleMap
    var TYPE_MINE = 0
    var TYPE_TARGET = 1
    var TYPE_MY_MAP = 2
    var TYPE_TARGET_MAP = 3
    var TYPE_MY_IMAGE = 4
    var TYPE_TARGET_IMAGE = 5
    val TYPE_MY_VIDEO = 6
    val TYPE_TARGET_VIDEO = 7


    val RQ_CAMERA = 100
    val RQ_GALLERY = 101
    val RQ_PREVIEW_IMAGE = 102
    val RQ_LOCATION = 103
    val RQ_VIDEO = 104

    val RP_STORAGE_GALLERY = 101
    val RP_LOCATION = 102
    val RP_STORAGE_CAMERA = 103
    val RP_STORAGE_VIDEO = 104

    val RP_INITAL_STORAGE_PERMISSION = 105

    var targetUid : String = ""
    var myUID : String = ""

    var imageFile:File? = null
     var cameraImagePath  = ""
     var cameraImageUri: Uri? = null

    var user1 = "user---1"
    var user2 = "user---2"

    val storage_dir_initial = "/storage/"

    var isBlockedByMe = false
    var isBlockedByUser = false

    val context = this@MessageActivity
    var loadedPosition:HashMap<Int,Boolean> = HashMap()

    var myLastMessagePosition = 0

    var selectedMessageIDs:MutableList<String> = ArrayList()
    var selectMessageModel:MutableList<Models.MessageModel> = ArrayList()
    var selectedItemViews:MutableList<View> = ArrayList()
    var searchFilterItemPosition:MutableList<Int> = ArrayList()

    var headerPosition:MutableList<Int> = ArrayList()

    val allMessages:HashMap<Models.MessageModel, Int> = HashMap()


    val isUploading:HashMap<String,Boolean> = HashMap()
    val CircularProgressBarsAt:HashMap<String,CircularProgressBar> = HashMap()
    val mediaControlImageViewAt:HashMap<String,ImageView> = HashMap()

    lateinit var adapter:FirebaseRecyclerAdapter<Models.MessageModel, RecyclerView.ViewHolder>



    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContentView(R.layout.activity_message)
        setSupportActionBar(toolbar)
        targetUid = intent.getStringExtra(FirebaseUtils.KEY_UID)
        myUID = FirebaseUtils.getUid()

        FirebaseUtils.loadProfileThumbnail(context, targetUid, profile_circleimageview)

        val emojiConfig = BundledEmojiCompatConfig(this)
        EmojiCompat.init(emojiConfig)
            .registerInitCallback(object:EmojiCompat.InitCallback() {
                override fun onInitialized() {
                    setRecyclerAdapter()
                    super.onInitialized()
                }
            })



      //  myUID = FirebaseUtils.user_voda
      //  targetUid = FirebaseUtils.user_jio



     //   myUID = user2
      //  targetUid = user1


        layout_toolbar_title.setOnClickListener {
            startActivity(Intent(this, UserProfileActivity::class.java)
                .putExtra(FirebaseUtils.KEY_UID, targetUid)
            )
        }

        back_layout_toolbar_message.setOnClickListener { finish() }




        Log.d("MessageActivity", "onCreate: myUID = "+myUID)
        Log.d("MessageActivity", "onCreate: target UID = "+targetUid)

        setSendMessageListener()




        checkIfBlocked(targetUid)

        setMenuListeners()

        attachment_menu.visibility = View.INVISIBLE

        messageInputField.setAttachmentsListener {


            if(isBlockedByUser || isBlockedByMe)
                return@setAttachmentsListener


         if(attachment_menu.visibility != View.VISIBLE) {
             utils.setEnterRevealEffect(attachment_menu)
           //  messagesList.alpha = 0.6f

         }
         else {
             utils.setExitRevealEffect(attachment_menu)
            // messagesList.alpha = 1f
         }


        }

    }


    private fun setMenuListeners(){

        val galleryIntent = Intent(context, ImagePickActivity::class.java)
        galleryIntent.putExtra(ImagePickActivity.IS_NEED_CAMERA, true)
        galleryIntent.putExtra(Constant.MAX_NUMBER,5)

        val videoIntent = Intent(context, VideoPickActivity::class.java)
        videoIntent.putExtra(VideoPickActivity.IS_NEED_CAMERA, true)
        videoIntent.putExtra(Constant.MAX_NUMBER, 5)

        camera_btn.setOnClickListener {

            if(attachment_menu.visibility != View.VISIBLE) utils.setEnterRevealEffect(attachment_menu) else utils.setExitRevealEffect(attachment_menu)


            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                if (ActivityCompat.checkSelfPermission(
                        context,
                        Manifest.permission.READ_EXTERNAL_STORAGE
                    ) == PackageManager.PERMISSION_GRANTED  && ActivityCompat.checkSelfPermission(
                        context,
                        Manifest.permission.WRITE_EXTERNAL_STORAGE
                    ) == PackageManager.PERMISSION_GRANTED) {

                    startCamera()

                }
                else {
                    requestPermissions(arrayOf(Manifest.permission.READ_EXTERNAL_STORAGE, Manifest.permission.WRITE_EXTERNAL_STORAGE), RP_STORAGE_CAMERA)
                }

            }
            else{
                startCamera()
            }
        }


        gallery_btn.setOnClickListener {

            if(attachment_menu.visibility != View.VISIBLE) utils.setEnterRevealEffect(attachment_menu) else utils.setExitRevealEffect(attachment_menu)

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                if (ActivityCompat.checkSelfPermission(
                        context,
                        Manifest.permission.READ_EXTERNAL_STORAGE
                    ) == PackageManager.PERMISSION_GRANTED && ActivityCompat.checkSelfPermission(
                        context,
                        Manifest.permission.WRITE_EXTERNAL_STORAGE
                    ) == PackageManager.PERMISSION_GRANTED){

                    startActivityForResult(galleryIntent, RQ_GALLERY)

                }

                else {
                    requestPermissions(arrayOf(Manifest.permission.READ_EXTERNAL_STORAGE, Manifest.permission.WRITE_EXTERNAL_STORAGE), RP_STORAGE_GALLERY)
                }

            }
            else{
                startActivityForResult(galleryIntent, RQ_GALLERY)
            }
        }


        location_btn.setOnClickListener {

            if(attachment_menu.visibility != View.VISIBLE) utils.setEnterRevealEffect(attachment_menu) else utils.setExitRevealEffect(attachment_menu)

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                if (ActivityCompat.checkSelfPermission(
                        context,
                        Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED)
                    startActivityForResult(Intent(context, MapsActivity::class.java), RQ_LOCATION)
                else
                    requestPermissions(arrayOf(Manifest.permission.ACCESS_FINE_LOCATION), RP_LOCATION)

            }
            else{
                startActivityForResult(Intent(context, MapsActivity::class.java), RQ_LOCATION)
            }
        }


        video_pick_btn.setOnClickListener {
            if(attachment_menu.visibility != View.VISIBLE) utils.setEnterRevealEffect(attachment_menu)
            else utils.setExitRevealEffect(attachment_menu)

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                if (ActivityCompat.checkSelfPermission(
                        context,
                        Manifest.permission.READ_EXTERNAL_STORAGE
                    ) == PackageManager.PERMISSION_GRANTED && ActivityCompat.checkSelfPermission(
                        context,
                        Manifest.permission.WRITE_EXTERNAL_STORAGE
                    ) == PackageManager.PERMISSION_GRANTED){

                    startActivityForResult(videoIntent, RQ_VIDEO)

                }

                else {
                    requestPermissions(arrayOf(Manifest.permission.READ_EXTERNAL_STORAGE, Manifest.permission.WRITE_EXTERNAL_STORAGE), RP_STORAGE_GALLERY)
                }

            }
            else{
                startActivityForResult(videoIntent, RQ_VIDEO)
            }

        }

    }

    private fun startCamera(){
        val intent = Intent(MediaStore.ACTION_IMAGE_CAPTURE)
        val timeStamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.ENGLISH).format(Date())
        val file = File(
            Environment.getExternalStoragePublicDirectory(DIRECTORY_DCIM).absolutePath
                    + "/IMG_" + timeStamp + ".jpg"
        )
        val mImagePath = file.absolutePath
        cameraImagePath = mImagePath

        val contentValues = ContentValues(1)
        contentValues.put(MediaStore.Images.Media.DATA, mImagePath)
        val mImageUri = context.contentResolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, contentValues)
        cameraImageUri = mImageUri

        intent.putExtra(MediaStore.EXTRA_OUTPUT, mImageUri)

        startActivityForResult(intent, RQ_CAMERA)
    }


    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {

        when(requestCode){
            RP_STORAGE_GALLERY -> {
                if(grantResults[0] == PackageManager.PERMISSION_GRANTED && grantResults[1] == PackageManager.PERMISSION_GRANTED) {
                    val galleryIntent = Intent(context, ImagePickActivity::class.java)
                    galleryIntent.putExtra(ImagePickActivity.IS_NEED_CAMERA, true)
                    galleryIntent.putExtra(Constant.MAX_NUMBER,5)
                    startActivityForResult(galleryIntent, RQ_GALLERY)
                }
                else
                    utils.toast(context, "Permission denied")
            }
            RP_STORAGE_CAMERA -> {
                if(grantResults[0] == PackageManager.PERMISSION_GRANTED && grantResults[1] == PackageManager.PERMISSION_GRANTED) {
                    startCamera()
                }
                else
                    utils.toast(context, "Permission denied")
            }
            RP_LOCATION -> {
                if(grantResults[0] == PackageManager.PERMISSION_GRANTED)
                    startActivityForResult(Intent(context, MapsActivity::class.java), RQ_LOCATION)
                else
                    utils.toast(context, "Permission denied")
            }


            RP_INITAL_STORAGE_PERMISSION -> {
                if (grantResults[0] == PackageManager.PERMISSION_GRANTED && grantResults[1] == PackageManager.PERMISSION_GRANTED) {
                    setRecyclerAdapter()
                } else {
                    utils.toast(context, "Permission denied")
                    finish()
                }
            }
        }

    }


    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {


        if(resultCode == Activity.RESULT_CANCELED && requestCode == RQ_CAMERA){
            contentResolver.delete(cameraImageUri, null,null)
        }


        if(resultCode != Activity.RESULT_OK)
            return


        var messageID = "MSG" +System.currentTimeMillis()


        if(requestCode == RQ_GALLERY ){


            val filePaths = data!!.getParcelableArrayListExtra<ImageFile>(Constant.RESULT_PICK_IMAGE)



            if(filePaths.isEmpty())
                return


            startActivityForResult(Intent(context, UploadPreviewActivity::class.java)
                .putParcelableArrayListExtra(utils.constants.KEY_IMG_PATH, filePaths)
                .putExtra(utils.constants.IS_FOR_SINGLE_FILE, false)
                .putExtra(utils.constants.KEY_FILE_TYPE, utils.constants.FILE_TYPE_IMAGE)
                , RQ_PREVIEW_IMAGE)




         }




        else if(requestCode == RQ_LOCATION){

            val latitude = data!!.getDoubleExtra(utils.constants.KEY_LATITUDE,0.0)
            val longitude = data.getDoubleExtra(utils.constants.KEY_LONGITUDE,0.0)
            val address = data.getStringExtra(utils.constants.KEY_ADDRESS)

            if(latitude == 0.0 || longitude == 0.0){
                utils.toast(context, "Failed to fetch location")
                return
            }

            val message = "$latitude,$longitude"

            addMessageToBoth(messageID, Models.MessageModel(message,
                myUID,
                targetUid,
                System.currentTimeMillis(),
                isFile = false,
                caption = address,
                messageType = utils.constants.FILE_TYPE_LOCATION))
        }


        else if(requestCode == RQ_CAMERA){
            val file = File(cameraImagePath)
            Log.d("MessageActivity", "onActivityResult: "+file.path)

            if(file.path.isEmpty()){
                utils.toast(context, "Failed to capture image")
                return
            }


            startActivityForResult(Intent(context, UploadPreviewActivity::class.java)
                .putExtra(utils.constants.KEY_IMG_PATH, file.path)
                .putExtra(utils.constants.IS_FOR_SINGLE_FILE, true)
                .putExtra(utils.constants.KEY_FILE_TYPE, utils.constants.FILE_TYPE_IMAGE)
                , RQ_PREVIEW_IMAGE)

        }


        else if(requestCode == RQ_VIDEO){
            val videoPaths = data!!.getParcelableArrayListExtra<VideoFile>(Constant.RESULT_PICK_VIDEO)


            startActivityForResult(Intent(context, UploadPreviewActivity::class.java)
                .putParcelableArrayListExtra(utils.constants.KEY_IMG_PATH, videoPaths)
                .putExtra(utils.constants.IS_FOR_SINGLE_FILE, false)
                .putExtra(utils.constants.KEY_FILE_TYPE, utils.constants.FILE_TYPE_VIDEO)
                , RQ_PREVIEW_IMAGE)


            for(videoFile in videoPaths){
                Log.d("MessageActivity", "onActivityResult: path = "+videoFile.path)
                Log.d("MessageActivity", "onActivityResult: duration = "+videoFile.duration)
                Log.d("MessageActivity", "onActivityResult: size = "+videoFile.size / (1024) +" KB")
                messageID = "MSG" +System.currentTimeMillis()

               // uploadFile(messageID, File(videoFile.path), "", utils.constants.FILE_TYPE_VIDEO, false)


            }
        }


        // after returning from preview
        else if(requestCode == RQ_PREVIEW_IMAGE ){
            val caption = data!!.getStringArrayListExtra(utils.constants.KEY_CAPTION)
            val imgPaths = data.getStringArrayListExtra(utils.constants.KEY_IMG_PATH)

            if (imgPaths.isNotEmpty()) {

                Log.d("MessageActivity", "onActivityResult: Uploading Image")


                for((index, path) in imgPaths.withIndex()) {
                    messageID = "MSG" +System.currentTimeMillis()

                    uploadFile(
                        messageID, File(path.toString()),
                        caption[index],
                        data.getStringExtra(utils.constants.KEY_FILE_TYPE),
                        true
                    )
                }

            }

        }

        super.onActivityResult(requestCode, resultCode, data)

    }



   private fun setRecyclerAdapter(){


       if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
           if (ActivityCompat.checkSelfPermission(
                   context,
                   Manifest.permission.READ_EXTERNAL_STORAGE
               ) == PackageManager.PERMISSION_GRANTED  && ActivityCompat.checkSelfPermission(
                   context,
                   Manifest.permission.WRITE_EXTERNAL_STORAGE
               ) == PackageManager.PERMISSION_GRANTED) {

           }
           else {
               requestPermissions(arrayOf(Manifest.permission.READ_EXTERNAL_STORAGE, Manifest.permission.WRITE_EXTERNAL_STORAGE), RP_INITAL_STORAGE_PERMISSION)
                return
           }

       }


       messagesList.setHasFixedSize(true)
       messagesList.setItemViewCacheSize(20)
       messagesList.isDrawingCacheEnabled = true;
       messagesList.drawingCacheQuality = View.DRAWING_CACHE_QUALITY_HIGH

       val linearLayoutManager = LinearLayoutManager(this)

        linearLayoutManager.stackFromEnd = true

        messagesList.layoutManager = linearLayoutManager


        val options = FirebaseRecyclerOptions.Builder<Models.MessageModel>()
            .setQuery(FirebaseUtils.ref.getChatRef(myUID, targetUid)

               // .limitToLast(20)
                ,Models.MessageModel::class.java)
            .build()


         adapter = object  : FirebaseRecyclerAdapter<Models.MessageModel, RecyclerView.ViewHolder>(options) {

            override fun onCreateViewHolder(p0: ViewGroup, viewType: Int): RecyclerView.ViewHolder {


               return when(viewType) {
                     TYPE_MINE ->
                       holders.MyTextMsgHolder(LayoutInflater.from(this@MessageActivity)
                       .inflate(R.layout.bubble_right, p0 , false))

                     TYPE_MY_MAP ->
                         holders.MyMapHolder(LayoutInflater.from(this@MessageActivity)
                             .inflate(R.layout.bubble_map_right, p0, false))


                     TYPE_TARGET_MAP ->
                       holders.TargetMapHolder(LayoutInflater.from(this@MessageActivity)
                           .inflate(R.layout.bubble_map_left, p0, false))

                     TYPE_MY_IMAGE ->
                         holders.MyImageMsgHolder(LayoutInflater.from(this@MessageActivity)
                             .inflate(R.layout.bubble_image_right, p0, false))

                   TYPE_TARGET_IMAGE ->
                       holders.TargetImageMsgHolder(LayoutInflater.from(this@MessageActivity)
                           .inflate(R.layout.bubble_image_left, p0, false))

                   TYPE_MY_VIDEO ->
                       holders.MyVideoMsgHolder(LayoutInflater.from(this@MessageActivity)
                           .inflate(R.layout.bubble_video_right, p0, false))

                   TYPE_TARGET_VIDEO ->
                       holders.TargetVideoMsgHolder(LayoutInflater.from(this@MessageActivity)
                           .inflate(R.layout.bubble_video_left, p0, false))



                   else -> holders.TargetTextMsgHolder(LayoutInflater.from(this@MessageActivity)
                           .inflate(R.layout.bubble_left, p0, false))
               }
            }


            @SuppressLint("ObjectAnimatorBinding")
            override fun onBindViewHolder(
                holder: RecyclerView.ViewHolder,
                position: Int,
                model: Models.MessageModel) {


                messagesList.setBackgroundColor(Color.WHITE)


                var messageImage:ImageView? = null
                var videoLayout:View? = null
                var dateHeader:TextView? = null
                var latitude: Double = 0.0
                var longitude: Double = 0.0
                var mapView: MapView? = null
                val messageID = super.getRef(position).key!!
                var thumbnail:ImageView? = null
                var videoLengthTextView:TextView? = null

                var container:LinearLayout? = null

                var circularProgressBar:CircularProgressBar? = null

                var tapToDownload:TextView? = null
                var messageTextView:TextView? = null

                var messageLayout:LinearLayout? = null


                val date = Date(model.timeInMillis)

                if(model.messageType == utils.constants.FILE_TYPE_LOCATION){


                    try {
                    latitude = model.message.split(",")[0].toDouble()
                    longitude = model.message.split(",")[1].toDouble()
                    }
                    catch (e :Exception){}

                }



                when (holder) {
                    is holders.TargetTextMsgHolder -> {
                        holder.time.text = utils.getLocalTime(model.timeInMillis)
                        holder.message.text = model.message
                        container = holder.container
                        messageTextView = holder.message
                        messageLayout = holder.messageLayout
                        dateHeader = holder.headerDateTime

                        if(position==0){
                            FirebaseUtils.loadProfileThumbnail(context, targetUid, holder.senderIcon)
                            holder.senderIcon.visibility = View.VISIBLE }
                        else{
                            if(model.from == snapshots[position -1 ].from) holder.senderIcon.visibility = View.INVISIBLE
                            else {
                                holder.senderIcon.visibility = View.VISIBLE
                                FirebaseUtils.loadProfileThumbnail(context, targetUid, holder.senderIcon)
                            }
                        }
                    }
                    is holders.MyTextMsgHolder -> {
                        holder.time.text = utils.getLocalTime(model.timeInMillis)
                        holder.message.text = model.message
                        dateHeader = holder.headerDateTime
                        container = holder.container
                        FirebaseUtils.setDeliveryStatusTick(targetUid, messageID, holder.messageStatus)
                        messageTextView = holder.message
                        messageLayout = holder.messageLayout

                        //end of my holder

                    }
                    is holders.MyImageMsgHolder -> {


                        messageImage = holder.imageView
                        container = holder.container
                        dateHeader = holder.headerDateTime

                        FirebaseUtils.setDeliveryStatusTick(targetUid, messageID, holder.messageStatus)


                        messageTextView = holder.message
                        messageLayout = holder.messageLayout


                        //setting holder config
                        setMyImageHolder(holder, model, messageID)

                    }
                    is holders.TargetImageMsgHolder -> {
                        messageImage = holder.imageView
                        dateHeader = holder.headerDateTime
                        container = holder.container

                        messageTextView = holder.message
                        messageLayout = holder.messageLayout

                        //setting holder setting
                        setTargetImageHolder(holder, model, messageID)

                        if(position==0){
                            FirebaseUtils.loadProfileThumbnail(context, targetUid, holder.senderIcon)
                            holder.senderIcon.visibility = View.VISIBLE }
                        else{
                            if(model.from == snapshots[position -1 ].from) holder.senderIcon.visibility = View.INVISIBLE
                            else {
                                holder.senderIcon.visibility = View.VISIBLE
                                FirebaseUtils.loadProfileThumbnail(context, targetUid, holder.senderIcon)
                            }
                        }
                    }
                    is holders.MyVideoMsgHolder -> {

                        thumbnail = holder.thumbnail
                        videoLengthTextView = holder.videoLengthText

                        tapToDownload = holder.tap_to_download
                        dateHeader = holder.headerDateTime
                        container = holder.container


                        FirebaseUtils.setDeliveryStatusTick(targetUid, messageID, holder.messageStatus)
                        messageTextView = holder.message
                        messageLayout = holder.messageLayout

                        //setting holder config
                        setMyVideoHolder(holder, model, messageID)


                    }
                    is holders.TargetVideoMsgHolder -> {

                        tapToDownload = holder.tap_to_download

                        thumbnail = holder.thumbnail
                        videoLengthTextView = holder.videoLengthText
                        dateHeader = holder.headerDateTime
                        container = holder.container


                        messageTextView = holder.message
                        messageLayout = holder.messageLayout


                        //setting holder config
                        setTargetVideoHolder(holder, model, messageID)

                        if(position==0){
                            FirebaseUtils.loadProfileThumbnail(context, targetUid, holder.senderIcon)
                            holder.senderIcon.visibility = View.VISIBLE }
                        else{
                            if(model.from == snapshots[position -1 ].from) holder.senderIcon.visibility = View.INVISIBLE
                            else {
                                holder.senderIcon.visibility = View.VISIBLE
                                FirebaseUtils.loadProfileThumbnail(context, targetUid, holder.senderIcon)
                            }
                        }

                    }

                    is holders.MyMapHolder -> {
                        holder.message.text = model.caption
                        holder.message.visibility =  if(model.caption.isEmpty()) View.GONE else View.VISIBLE

                        messageLayout = holder.messageLayout
                        dateHeader = holder.dateHeader
                        messageTextView = holder.message

                        loadMap(holder.mapView, LatLng(latitude,longitude))

                    }

                    is holders.TargetMapHolder -> {

                        holder.message.text = model.caption
                        dateHeader = holder.dateHeader

                        messageLayout = holder.messageLayout
                        messageTextView = holder.message

                        loadMap(holder.mapView, LatLng(latitude,longitude))
                        if(position==0){
                            FirebaseUtils.loadProfileThumbnail(context, targetUid, holder.senderIcon)
                            holder.senderIcon.visibility = View.VISIBLE }
                        else{
                            if(model.from == snapshots[position -1 ].from) holder.senderIcon.visibility = View.INVISIBLE
                            else {
                                holder.senderIcon.visibility = View.VISIBLE
                                FirebaseUtils.loadProfileThumbnail(context, targetUid, holder.senderIcon)
                            }
                        }


                    }
                }





                //loading message Image listener
               if(messageImage!=null) {
                   messageImage.setOnClickListener {


                       if(!isContextMenuActive)
                           startActivity(
                               Intent(context, ImagePreviewActivity::class.java)
                                   .putExtra(utils.constants.KEY_IMG_PATH, model.message.toString())
                                   .putExtra(utils.constants.KEY_LOCAL_PATH, model.file_local_path.toString())
                           )


                   }
               }



                //setting video intent
                if(thumbnail != null){

                    if(model.file_local_path.isNotEmpty() && File(model.file_local_path).exists()){
                        videoLengthTextView!!.text = utils.getVideoLength(context, model.file_local_path)

                        utils.loadVideoThumbnailFromLocalAsync(context, thumbnail, model.file_local_path)

                            tapToDownload!!.visibility = View.GONE
                    }
                    else{

                        utils.setVideoThumbnailFromWebAsync(model.message, thumbnail)

                        Log.d("MessageActivity", "onBindViewHolder: $messageID file not found")

                            tapToDownload!!.visibility = View.VISIBLE


                            tapToDownload.setOnClickListener {

                                if(isContextMenuActive)
                                    return@setOnClickListener

                                downloadVideo(messageID)
                                it.visibility = View.GONE
                            }

                    }

                    thumbnail.setOnClickListener {


                        if(isContextMenuActive)
                            return@setOnClickListener

                        if(model.file_local_path.isNotEmpty() && File(model.file_local_path).exists()) {

                            utils.startVideoIntent(context, model.file_local_path)
                        }
                        else {
                         //   downloadVideo(messageID, holder.progressBar)
                            utils.toast(context, "File not found on the device")
                        }

                    }

                }




                val emojiProcessed = EmojiCompat.get().process(messageTextView!!.text)
                messageTextView.text = emojiProcessed
                Log.d("MessageActivity", "onBindViewHolder: $emojiProcessed")

                //set date Header


                when {
                    DateFormatter.isToday(date) -> dateHeader!!.text ="Today"
                    DateFormatter.isYesterday(date) -> dateHeader!!.text ="Yesterday"
                    else -> dateHeader!!.text = utils.getLocalDate(model.timeInMillis)
                }


                if(position>0){

                    val previousDate = Date(snapshots[position - 1].timeInMillis)

                    dateHeader.visibility =  if(!DateFormatter.isSameDay(date ,  previousDate)){ View.VISIBLE }
                    else{ View.GONE }

                }
                else{

                    dateHeader.visibility = View.VISIBLE

                }

                dateHeader.setPadding(20,80,20,80)



                //setting contextual toolbar
                setContextualToolbarOnViewHolder(messageLayout as View, messageID, model)



                if(searchFilterItemPosition.contains(position) ){
                    utils.highlightTextView(messageTextView, searchQuery, Color.parseColor("#51C1EE"))

                    if(selectedPosition == position) {
                        val fadeAnim = ObjectAnimator.ofObject(messageLayout, "backgroundColor",
                            ArgbEvaluator(),Color.parseColor("#51C1EE"), Color.WHITE)
                        fadeAnim.duration = 2000
                        fadeAnim.start()
                        selectedPosition = -1
                    }
                }
                else{

                    messageTextView.text = if(model.isFile) model.caption else model.message
                }




               // holder.itemView.setPadding(0,10,0,10)


            }


            override fun getItemViewType(position: Int): Int {

                val model: Models.MessageModel = getItem(position)

                val viewType: Int

                viewType = if(model.from == myUID){

                    if(model.messageType == utils.constants.FILE_TYPE_LOCATION.toString()) {
                        TYPE_MY_MAP
                    }
                    else if(model.messageType == utils.constants.FILE_TYPE_IMAGE.toString()) {
                        TYPE_MY_IMAGE
                    }
                    else if(model.messageType == utils.constants.FILE_TYPE_VIDEO.toString()) {
                        TYPE_MY_VIDEO
                    }

                    else{
                        TYPE_MINE
                    }
                } else{
                    if(model.messageType == utils.constants.FILE_TYPE_LOCATION.toString())
                            TYPE_TARGET_MAP

                    else if(model.messageType == utils.constants.FILE_TYPE_IMAGE.toString()) {
                        TYPE_TARGET_IMAGE
                    }
                    else if(model.messageType == utils.constants.FILE_TYPE_VIDEO.toString()) {
                        TYPE_TARGET_VIDEO
                    }
                    else
                            TYPE_TARGET

                }


                return viewType


            }

        }

        messagesList.adapter = adapter


        adapter.registerAdapterDataObserver(object : RecyclerView.AdapterDataObserver() {
            override fun onItemRangeInserted(positionStart: Int, itemCount: Int) {

                if(adapter.getItem(positionStart).from == myUID)
                    messagesList.scrollToPosition(adapter.itemCount - 1)

                super.onItemRangeInserted(positionStart, itemCount)
            }
        })

       adapter.startListening()




    }




    private fun setSendMessageListener(){


        messageInputField.setInputListener {

            if(isBlockedByMe || isBlockedByUser) {
                return@setInputListener true
            }

            val message = messageInputField.inputEditText.text.toString()

            val messageModel= Models.MessageModel(message.trim() ,
                myUID, targetUid ,isFile = false)

            val messageID = "MSG" +System.currentTimeMillis()


            addMessageToBoth(messageID, messageModel)
            loadedPosition[messagesList.adapter!!.itemCount ]



            true
        }

    }


    private fun setTapToRetryBtn(
        tapToRetry:View, progressBar: CircularProgressBar, filePath:String, messageID: String,
        caption: String, fileType:String){


        Log.d("MessageActivity", "setTapToRetryBtn: caption ")

        tapToRetry.visibility = View.GONE

        FirebaseUtils.ref.getChatRef(myUID, targetUid)
            .child(messageID)
            .child("message")
            .addValueEventListener(object :ValueEventListener{
                override fun onDataChange(p0: DataSnapshot) {



                    if(p0.exists())
                    tapToRetry.visibility = if(p0.getValue(String::class.java)!!.isEmpty()) View.VISIBLE else View.GONE

                    if(isUploading[messageID] == true)
                        tapToRetry.visibility = View.GONE

                    Log.d("MessageActivity", "onDataChange: tap to retry changed to : visible = "+(tapToRetry.visibility == View.VISIBLE))

                }

                override fun onCancelled(p0: DatabaseError) {
                }
            })

        tapToRetry.setOnClickListener {
          progressBar.visibility = View.VISIBLE
            it.visibility = View.GONE

            if(File(filePath).exists())
                uploadFile(
                    messageID, File(filePath.toString()),
                    caption, fileType, false
                )
            else{
                utils.toast(context, "File does not exists on this device")
                progressBar.visibility = View.GONE
            }
        }
    }

    private fun loadMap(mapView: MapView, latLng: LatLng){
        //loading a map

        try {
            mapView.run {
                onCreate(null)

                getMapAsync { googleMap ->


                    googleMap!!.addMarker(
                        MarkerOptions()
                            .position(latLng).title("")
                            .icon(BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_RED))
                            .draggable(false).visible(true)
                    )


                    googleMap.uiSettings.setAllGesturesEnabled(false)
                    googleMap.moveCamera(
                        CameraUpdateFactory.newLatLngZoom(
                            latLng,
                            12F
                        )
                    )

                    Log.d("MessageActivity", "onMapReady: ")
                }
            }
        }
        catch (e:Exception){}

    }



    private fun addMessageToBoth(messageID: String , messageModel: Models.MessageModel){

        //setting  message to both


        addMessageToMyNode(messageID, messageModel)

        addMessageToTargetNode(messageID, messageModel)


    }


    private fun addMessageToMyNode(messageID: String , messageModel: Models.MessageModel){

        //setting my message

        FirebaseUtils.ref.getChatRef(myUID, targetUid)
            .child(messageID)
            .setValue(messageModel)
            .addOnSuccessListener {

                FirebaseUtils.setMessageStatusToDB(messageID, myUID, true, true)

                FirebaseUtils.ref.getLastMessageRef(myUID)
                    .child(targetUid)
                    .setValue(Models.LastMessageDetail())

                print("Message sent to $targetUid") }

    }



    private fun addMessageToTargetNode(messageID: String , messageModel: Models.MessageModel) {

        //setting  message to target
        FirebaseUtils.ref.getChatRef(targetUid, myUID)
            .child(messageID)
            .setValue(messageModel)
            .addOnSuccessListener {

                FirebaseUtils.setMessageStatusToDB(messageID, targetUid, false, false)

                FirebaseUtils.ref.getLastMessageRef(targetUid)
                    .child(myUID)
                    .setValue(Models.LastMessageDetail())

                print("Message added to mine") }

    }


    //upload
    private fun uploadFile(
        messageID: String,
        file: File,
        caption: String,
        messageType: String,
        isNewIDRequired:Boolean
    ){


        Log.d("MessageActivity", "fileUpload: dir = "+file.path)


        val originalPath = file.path



        //Initial node
        var messageModel= Models.MessageModel(
            "",
            myUID, targetUid ,isFile = true, caption = caption, messageType = messageType, file_local_path = originalPath,
            file_size_in_bytes = file.length())


        isUploading[messageID] = true





        when(messageType) {

            utils.constants.FILE_TYPE_IMAGE -> {

                Log.d("MessageActivity", "uploadFile: image")
                //image compressor
                Luban.compress(context, File(file.path))
                    .putGear(Luban.THIRD_GEAR)
                    .clearCache()
                    .launch(object : OnCompressListener {
                        override fun onError(e: Throwable?) {

                            utils.toast(context, e!!.message.toString())
                        }

                        override fun onStart() {

                        }

                        override fun onSuccess(file: File?) {

                            //setting up node for compressed image
                            messageModel= Models.MessageModel(
                                "",
                                myUID, targetUid ,isFile = true,
                                caption = caption, messageType = messageType,
                                file_local_path = originalPath,
                                file_size_in_bytes = file!!.length())



                            val fileSizeInMB = (file.length()/(1024* 1024))


                            val newID = if(isNewIDRequired) "MSG" +System.currentTimeMillis() else messageID

                            isUploading[newID] = true


                            Log.d("MessageActivity", "uploadFile: file size = $fileSizeInMB")

                            if(fileSizeInMB > 16){
                                utils.toast(context, "File size exceeded by 16 MB, Please choose a smaller file")
                                return
                            }

                            fileUpload(newID, file, originalPath, caption, messageType)

                            addMessageToMyNode(newID, messageModel)

                        }
                    })
            }



            //for video upload
            utils.constants.FILE_TYPE_VIDEO -> {


                //for video file check
                val fileSizeInMB = (file.length()/(1024* 1024))

                Log.d("MessageActivity", "uploadFile: file size = $fileSizeInMB")

                if(fileSizeInMB > 16){
                    utils.longToast(context, "File size exceeded by 16 MB, Please choose a smaller file")
                    return
                }


                Log.d("MessageActivity", "uploadFile: video")
                fileUpload(messageID, file, originalPath, caption, messageType)

                addMessageToMyNode(messageID, messageModel)


            }


        }




    }


    private fun fileUpload(
        messageID: String,
        file: File,
        originalFinalPath: String,
        caption: String,
        messageType: String
    ) {


        val ref =  FirebaseStorage.getInstance()
            .reference.child(messageType).child(messageID)

        val uploadTask = ref.putFile(utils.getUriFromFile(context, file))



        //setting initial value
        if(CircularProgressBarsAt.containsKey(messageID)){
            if(CircularProgressBarsAt[messageID]!=null){
                CircularProgressBarsAt[messageID]!!.progress = 0f
                CircularProgressBarsAt[messageID]!!.enableIndeterminateMode(true)

            }
        }




        uploadTask.addOnProgressListener { taskSnapshot ->



                val percentage:Double = (100.0 * taskSnapshot.bytesTransferred) / taskSnapshot.totalByteCount


                if(CircularProgressBarsAt.containsKey(messageID)){
                    if(CircularProgressBarsAt[messageID]!=null){
                        if(percentage.toInt() < 5){
                            CircularProgressBarsAt[messageID]!!.enableIndeterminateMode(true)
                        }
                        else{
                            CircularProgressBarsAt[messageID]!!.enableIndeterminateMode(false)
                        }

                        CircularProgressBarsAt[messageID]!!.progress = percentage.toFloat()

                    }

                }


            //setting cancel button value
            if(mediaControlImageViewAt.containsKey(messageID)){

                if(mediaControlImageViewAt[messageID]!=null){

                    val btnView = mediaControlImageViewAt[messageID]


                    btnView!!.setOnClickListener {


                        if(percentage >= 100)
                            return@setOnClickListener

                        Log.d("MessageActivity", "fileUpload: cancel clicked")
                        if(BuildConfig.DEBUG)
                            utils.toast(context, "Upload cancelled")


                        uploadTask.cancel()
                        mediaControlImageViewAt[messageID]!!.setImageResource(R.drawable.ic_play_white)
                        adapter.notifyDataSetChanged()
                    }
                }
            }




             }
            .continueWithTask(Continuation<UploadTask.TaskSnapshot, Task<Uri>> { task ->
                if (!task.isSuccessful) {
                    task.exception?.let {
                        throw it
                    }
                }
                return@Continuation ref.downloadUrl
            })
            .addOnCanceledListener {
                isUploading[messageID] = false
                if(CircularProgressBarsAt[messageID]!=null)
                    CircularProgressBarsAt[messageID]!!.visibility = View.GONE

                Log.d("MessageActivity", "fileUpload: upload cancelled")

            }
            .addOnCompleteListener { task ->

                isUploading[messageID] = false



                if (task.isSuccessful) {
                    val link = task.result
                    val targetModel = Models.MessageModel(
                        link.toString(),
                        myUID, targetUid, isFile = true, caption = caption, messageType = messageType,
                        file_size_in_bytes = file.length()
                    )

                    if (BuildConfig.DEBUG)
                        utils.toast(context, "Uploaded")


                    addMessageToTargetNode(messageID, targetModel)


                    val myModel = Models.MessageModel(
                        link.toString(),
                        myUID, targetUid,
                        isFile = true, caption = caption, messageType = messageType,
                        file_local_path = originalFinalPath,
                        file_size_in_bytes = file.length()
                    )

                    addMessageToMyNode(messageID, myModel)


                } else {

                      //  utils.longToast(context, "Upload failed. Your daily upload/download limit might have been exceeded. Please try again tomorrow")


                    Log.e("MessageActivity", "fileUpload: error in upload : "+task.exception!!.toString())
                    task.exception!!.printStackTrace()
                }
            }




    }


    //downloading video and saving to file in the form of file
    private fun downloadVideo(messageID: String){


        val progressBar = CircularProgressBarsAt[messageID]

        progressBar!!.visibility = View.VISIBLE
        progressBar.progress =0f

        val storageRef = FirebaseStorage.getInstance().reference
            .child(utils.constants.FILE_TYPE_VIDEO).child(messageID)



            val videoFile = utils.getVideoFile(context, messageID)

            storageRef.getFile(videoFile)
                .addOnProgressListener {
                    val percentage:Double = (100.0 * it.bytesTransferred) / it.totalByteCount
                    progressBar.progress = percentage.toFloat()

                }
                .addOnCompleteListener{
                    progressBar.visibility = View.GONE
                    try {
                       // adapter.notifyDataSetChanged()
                    }
                    catch (e:Exception){}
                }
                .addOnCanceledListener {
                    progressBar.visibility = View.GONE
                }
                .addOnSuccessListener {

                    FirebaseUtils.ref.getChatRef(myUID,targetUid)
                        .child(messageID)
                        .child(FirebaseUtils.KEY_FILE_LOCAL_PATH)
                        .setValue(videoFile.path)
                }





    }




    override fun onStart() {
        super.onStart()

        try {

            adapter.startListening()

        } catch (e:Exception){}
    }


    override fun onDestroy() {
        super.onDestroy()

        try {

            adapter.stopListening()

    } catch (e:Exception){}

}


    var blockedSnackbar:Snackbar? = null

    private fun checkIfBlocked(targetUID:String) {

        blockedSnackbar =   Snackbar.make(messageInputField, "You cannot reply to this conversation anymore", Snackbar.LENGTH_INDEFINITE)


        //check if i have blocked
        FirebaseUtils.ref.getBlockedUserRef(myUID, targetUID)
            .addValueEventListener(object : ValueEventListener {
                override fun onDataChange(dataSnapshot: DataSnapshot) {

                    isBlockedByMe = if (dataSnapshot.exists())
                        dataSnapshot.getValue(Boolean::class.java)!!
                    else
                        false


                    if(isBlockedByUser || isBlockedByMe) {
                        messageInputField.visibility = View.INVISIBLE
                        blockedSnackbar!!.show()
                    }
                    else {
                        messageInputField.visibility = View.VISIBLE
                        blockedSnackbar!!.dismiss()
                    }


                    invalidateOptionsMenu()

                }

                override fun onCancelled(databaseError: DatabaseError) {

                }
            })
        //check i am blocked by user

        FirebaseUtils.ref.getBlockedUserRef(targetUID, myUID)
            .addValueEventListener(object : ValueEventListener {
                override fun onDataChange(dataSnapshot: DataSnapshot) {

                    isBlockedByUser = if (dataSnapshot.exists())
                        dataSnapshot.getValue(Boolean::class.java)!!
                    else
                        false


                    if(isBlockedByUser || isBlockedByMe) {
                        messageInputField.visibility = View.INVISIBLE
                        blockedSnackbar!!.show()
                    }
                    else {
                        messageInputField.visibility = View.VISIBLE
                        blockedSnackbar!!.dismiss()
                    }
                }

                override fun onCancelled(databaseError: DatabaseError) {

                }
            })


    }


    override fun onResume() {
        invalidateOptionsMenu()
        super.onResume()
    }

    //setting my holders
    //setting my holder config
    private fun setMyImageHolder(holder: holders.MyImageMsgHolder, model: Models.MessageModel, messageID: String){
        holder.tapToRetry.visibility = View.GONE

        holder.progressBar.visibility = View.VISIBLE
        CircularProgressBarsAt[messageID] = holder.progressBar



        holder.message.visibility =  if(model.caption.isEmpty()) View.GONE else View.VISIBLE


        setTapToRetryBtn(holder.tapToRetry,holder.progressBar,model.file_local_path, messageID,model.caption, model.messageType)


        if(model.file_local_path.isNotEmpty() && File(model.file_local_path).exists()){

            //when image is not uploaded

            Picasso.get()
                .load(File(model.file_local_path.toString()))
                //  .resize(600,500)
                .fit()
                .centerCrop()
                .error(R.drawable.error_placeholder2)
                .placeholder(R.drawable.placeholder_image)
                .tag(model.message.toString())
                .into(holder.imageView, object: Callback{

                    override fun onSuccess() {
                        holder.progressBar.visibility = if(isUploading[messageID] == true) View.VISIBLE else View.GONE

                    }

                    override fun onError(e: Exception?) {

                        holder.progressBar.visibility = View.GONE

                        Log.d("MessageActivity", "onError: img file failed to load : " + e!!.message)
                    }

                })
        }

        else  {

            if(model.message.isNotEmpty())
                Picasso.get()
                    .load(model.message.toString())
                    .fit()
                    .centerCrop()
                    // .resize(600,400)
                    .error(R.drawable.error_placeholder2)
                    .placeholder(R.drawable.placeholder_image)
                    .tag(model.message.toString())
                    .into(holder.imageView, object: Callback{
                        override fun onSuccess() {

                            holder.progressBar.visibility = View.GONE
                            val bitmap = (holder.imageView.drawable as BitmapDrawable).bitmap
                            if(bitmap!=null){
                                val path = utils.saveBitmapToSent(context, bitmap, messageID)
                                FirebaseUtils.ref.getChatRef(myUID,targetUid)
                                    .child(messageID)
                                    .child(FirebaseUtils.KEY_FILE_LOCAL_PATH)
                                    .setValue(path)
                            }

                        }

                        override fun onError(e: Exception?) {

                            holder.progressBar.visibility = if(isUploading[messageID] == false) View.GONE else View.VISIBLE

                            Log.d("MessageActivity", "onError: img url failed to load")

                            holder.tapToRetry.setOnClickListener {

                                if(isContextMenuActive)
                                    return@setOnClickListener

                                it.visibility = View.GONE
                                utils.longToast(context, "Image might be deleted.")
                            }


                        }

                    })



        }

        holder.message.text = model.caption
    }

    //setting my video holder
    private fun setMyVideoHolder(holder:holders.MyVideoMsgHolder, model: Models.MessageModel, messageID: String){

        CircularProgressBarsAt[messageID] = holder.progressBar
        mediaControlImageViewAt[messageID] = holder.centerImageView



        setTapToRetryBtn(holder.tapToRetry,holder.progressBar,model.file_local_path, messageID,model.caption, model.messageType)


        holder.tapToRetry.visibility = View.GONE
        holder.progressBar.visibility = if(isUploading[messageID] == true) View.VISIBLE else View.GONE
        holder.message.visibility =  if(model.caption.isEmpty()) View.GONE else View.VISIBLE


        if(holder.progressBar.visibility == View.VISIBLE)
            holder.centerImageView.setImageResource(R.drawable.ic_clear_white_24dp)
        else
            holder.centerImageView.setImageResource(R.drawable.ic_play_white)

    }



    //setting target holders
    //setting target image holder
    private fun setTargetImageHolder(holder: holders.TargetImageMsgHolder, model:Models.MessageModel, messageID: String){
        holder.message.visibility =  if(model.caption.isEmpty()) View.GONE else View.VISIBLE

        holder.message.text = model.caption


        if(model.file_local_path.isNotEmpty() && File(model.file_local_path).exists()) {
            Picasso.get()
                .load(File(model.file_local_path.toString()))
                .tag(model.message.toString())
                .fit()
                .centerCrop()
                //.resize(600,400)
                .error(R.drawable.error_placeholder2)
                .placeholder(R.drawable.placeholder_image)
                .into(holder.imageView)



        }

        else{
            Picasso.get()
                .load(model.message.toString())
                .tag(model.message.toString())
                .centerCrop()
                .resize(600,400)
                .error(R.drawable.error_placeholder2)
                .placeholder(R.drawable.placeholder_image)
                .into( object : Target{
                    override fun onPrepareLoad(placeHolderDrawable: Drawable?) {
                    }

                    override fun onBitmapFailed(e: Exception?, errorDrawable: Drawable?) {
                    }

                    override fun onBitmapLoaded(bitmap: Bitmap?, from: Picasso.LoadedFrom?) {
                        val savedPath = utils.saveBitmapToReceived(context, bitmap!!, messageID)
                        Log.d("MessageActivity", "onBitmapLoaded: saved path = $savedPath")
                        holder.imageView.setImageBitmap(bitmap)

                        FirebaseUtils.ref.getChatRef(myUID,targetUid)
                            .child(messageID)
                            .child(FirebaseUtils.KEY_FILE_LOCAL_PATH)
                            .setValue(savedPath)
                    }

                })
        }
    }


    //setting target video holder
    private fun setTargetVideoHolder(holder: holders.TargetVideoMsgHolder, model: Models.MessageModel, messageID: String){

        holder.message.visibility =  if(model.caption.isEmpty()) View.GONE else View.VISIBLE
        holder.message.text = model.caption


        CircularProgressBarsAt[messageID] = holder.progressBar
        mediaControlImageViewAt[messageID] = holder.centerImageView

        //lets hide progressbar for now
        holder.progressBar.visibility = View.GONE




        if(holder.progressBar.visibility == View.VISIBLE)
            holder.centerImageView.setImageResource(R.drawable.ic_clear_white_24dp)
        else
            holder.centerImageView.setImageResource(R.drawable.ic_play_white)


        if(model.file_local_path.isEmpty()){
            downloadVideo(messageID)
        }
    }






    var selectedDrawable:Drawable? = null
    var unselectedDrawable:Drawable? = null

    //setting contextual toolbar on viewHolder

    var actionMode: ActionMode? = null
    private fun setContextualToolbarOnViewHolder(itemView: View, messageID: String, model:Models.MessageModel){

        selectedDrawable = ColorDrawable(ContextCompat.getColor(context, R.color.transparent_green))
        unselectedDrawable = ColorDrawable(Color.WHITE)



        //adding all views
        if(!selectedItemViews.contains(itemView)) {
            selectedItemViews.add(itemView)
        }


        itemView.setOnLongClickListener {

            if(!isContextMenuActive) {

                if(!selectedMessageIDs.contains(messageID)) {
                    selectedMessageIDs.add(messageID)
                    selectMessageModel.add(model)
                }

                actionMode = startSupportActionMode(actionModeCallback )

                actionMode!!.title = selectedMessageIDs.size.toString()


                it.background = if (it.background == selectedDrawable) unselectedDrawable else selectedDrawable

            }

            true
        }


        itemView.setOnClickListener {



            if(isContextMenuActive) {



                it.background = if (it.background == selectedDrawable) unselectedDrawable else selectedDrawable


                if(it.background == unselectedDrawable) {
                    selectedMessageIDs.remove(messageID)
                    selectMessageModel.remove(model)
                }
                else {
                    if (!selectedMessageIDs.contains(messageID)) {
                        selectedMessageIDs.add(messageID)
                        selectMessageModel.add(model)
                    }
                }



                if(actionMode!=null) {
                    actionMode!!.title = selectedMessageIDs.size.toString()
                    if(model.isFile)
                    actionMode!!.invalidate()
                }

                if(selectedMessageIDs.isEmpty()){
                    if(actionMode!=null) {
                        actionMode!!.finish()
                    }
                    

                }
            }

        }
    }




    var isContextMenuActive = false
    private var actionModeCallback = object : ActionMode.Callback{
        override fun onActionItemClicked(p0: ActionMode?, p1: MenuItem?): Boolean {


            Log.d("MessageActivity", "onActionItemClicked:Total messages = ${selectMessageModel.size}")

            when(p1!!.itemId) {

                R.id.action_delete -> {
                    for ((index, messageID) in selectedMessageIDs.withIndex()) {


//                        FirebaseUtils.ref.getChatRef(myUID, targetUid)
//                            .child(messageID)
//                            .child("message_deleted")
//                            .setValue(true)
//
//
//                        FirebaseUtils.ref.getChatRef( targetUid, myUID)
//                            .child(messageID)
//                            .child("message_deleted")
//                            .setValue(true)


                        FirebaseUtils.ref.getChatRef(myUID, targetUid)
                            .child(messageID)
                            .removeValue()
                            .addOnCompleteListener {
                                if (index == selectedMessageIDs.size - 1) {
                                    headerPosition.clear()
                                    adapter.notifyDataSetChanged()
                                }
                            }
                    }
                }

                R.id.action_copy ->{

                    var messages = ""
                    for(message in selectMessageModel) {
                        messages = if(message.isFile) messages + message.caption + "\n"
                                    else messages + message.message + "\n"

                    }

                    val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                    clipboard.primaryClip = (ClipData.newPlainText("Messages ",messages.trim()))

                    utils.toast(context, "Messages copied")
                    }

                R.id.action_forward -> {
                    startActivity(Intent(context, ForwardActivity::class.java)
                        .putExtra(utils.constants.KEY_MSG_MODEL, selectMessageModel as Serializable)
                    )
                }


            }

            p0!!.finish()

            return true
        }

        override fun onCreateActionMode(p0: ActionMode?, p1: Menu?): Boolean {

            p0!!.menuInflater.inflate(R.menu.chat_actions_menu, p1)

            isContextMenuActive = true

            return true
        }

        override fun onPrepareActionMode(p0: ActionMode?, p1: Menu?): Boolean {
            return true
        }

        override fun onDestroyActionMode(p0: ActionMode?) {
            isContextMenuActive = false
            selectedMessageIDs.clear()
            selectMessageModel.clear()
            headerPosition.clear()

            adapter.notifyDataSetChanged()

            for(view in selectedItemViews)
                view.setBackgroundColor(Color.WHITE)
        }

    }


    override fun onBackPressed() {

        if(attachment_menu.visibility == View.VISIBLE)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                utils.setExitRevealEffect(attachment_menu)
            }
            else
                attachment_menu.visibility = View.GONE
        else
            finish()

    }

    private var blockItem: MenuItem? = null
    private var selectedPosition = -1
    private var searchQuery = ""

    override fun onCreateOptionsMenu(menu: Menu?): Boolean {
        menuInflater.inflate(R.menu.message_activity_menu, menu)

        val searchView = menu!!.findItem(R.id.app_bar_search).actionView as SearchView


        blockItem = menu.findItem(R.id.menu_action_block)
        blockItem!!.title = if(isBlockedByMe) "Unblock" else "Block"


        setSearchView(searchView)



        return super.onCreateOptionsMenu(menu)
    }


    var searchPosition = 0

    private fun setSearchView(searchView: SearchView){



        searchView.maxWidth = Integer.MAX_VALUE
        searchView.isIconified = true


        val searchLayout = searchView.getChildAt(0) as LinearLayout
        val upBtn = ImageButton(context)
        val downBtn = ImageButton(context)


        upBtn.setImageResource(R.drawable.ic_up_white_24dp)
        upBtn.background = null
        upBtn.scaleType= ImageView.ScaleType.FIT_XY

        downBtn.setImageResource(R.drawable.ic__down_white_24dp)
        downBtn.background = null
        downBtn.scaleType= ImageView.ScaleType.FIT_XY

        upBtn.visibility = View.GONE
        downBtn.visibility = View.GONE

        searchLayout.addView(upBtn)
        searchLayout.addView(downBtn)


        searchView.setOnQueryTextListener(object : SearchView.OnQueryTextListener{
            override fun onQueryTextSubmit(p0: String?): Boolean {


                if(p0!!.isEmpty())
                    return true

                val query = p0
                var resultCount = 0
                searchFilterItemPosition.clear()
                searchPosition = 0
                selectedPosition = -1
                searchQuery = query.toString().trim().toLowerCase()

                for((index,model) in adapter.snapshots.withIndex().reversed()){


                    if(model.isFile){
                        if (model.caption.toLowerCase().contains(searchQuery)) {
                            searchFilterItemPosition.add((index))
                            resultCount++
                        }
                    }
                    else {
                        if (model.message.toLowerCase().contains(query.toString().toLowerCase())) {
                            searchFilterItemPosition.add(index)
                            resultCount++
                        }
                    }
                }


                if(resultCount>0) {
                    utils.toast(context, "$resultCount results found")
                    upBtn.visibility = View.VISIBLE
                    downBtn.visibility = View.VISIBLE
                    messagesList.scrollToPosition(searchFilterItemPosition[0])
                    selectedPosition = searchFilterItemPosition[0]
                }
                else{
                    utils.toast(context, "No result")
                    upBtn.visibility = View.GONE
                    downBtn.visibility = View.GONE

                }


                utils.hideSoftKeyboard(this@MessageActivity)

                adapter.notifyDataSetChanged()

                return true
            }

            override fun onQueryTextChange(p0: String?): Boolean = true

        })


        searchView.setOnCloseListener {

            upBtn.visibility = View.GONE
            downBtn.visibility = View.GONE

            selectedPosition = -1
            searchQuery = ""
            searchView.onActionViewCollapsed()
            searchFilterItemPosition.clear()

            adapter.notifyDataSetChanged()

            true
        }

        upBtn.setOnClickListener {

            if(searchFilterItemPosition.isEmpty())
                return@setOnClickListener

            if(searchPosition>=searchFilterItemPosition.size){
                searchPosition = 0
            }


            if(searchPosition>=0 && searchPosition<searchFilterItemPosition.count()) {
                selectedPosition = searchFilterItemPosition[searchPosition]
                messagesList.scrollToPosition(searchFilterItemPosition[searchPosition])

            }

            adapter.notifyDataSetChanged()

            searchPosition++
            utils.hideSoftKeyboard(this@MessageActivity)


        }

        downBtn.setOnClickListener {


            if(searchFilterItemPosition.isEmpty())
                return@setOnClickListener

            if(searchPosition<0){
                searchPosition = searchFilterItemPosition.size - 1
            }


            if(searchPosition>=0 && searchPosition<searchFilterItemPosition.count()) {
                selectedPosition = searchFilterItemPosition[searchPosition]
                messagesList.scrollToPosition(searchFilterItemPosition[searchPosition])

            }
            adapter.notifyDataSetChanged()

            searchPosition--
            utils.hideSoftKeyboard(this@MessageActivity)

        }

    }


    override fun onOptionsItemSelected(item: MenuItem?): Boolean {

        when(item!!.itemId){
            R.id.menu_action_block -> {

                AlertDialog.Builder(context).setMessage("${if (isBlockedByMe) "Unblock" else "Block"} this user")
                    .setPositiveButton("Yes") { _, _ ->
                        FirebaseUtils.ref.getBlockedUserRef(myUID, targetUid)
                            .setValue(!isBlockedByMe)
                    }
                    .setNegativeButton("No", null)
                    .show()

            }


            R.id.menu_action_clear -> {
                AlertDialog.Builder(context).setMessage("Clear all the messages from this user?")
                    .setPositiveButton("Yes, Please!") { _, _ ->
                        FirebaseUtils.ref.getChatRef(myUID, targetUid)
                            .removeValue()
                            .addOnCompleteListener {
                                utils.toast(context, if (it.isSuccessful) "Messages cleared" else "Failed to clear messages")
                            }
                    }
                    .setNegativeButton("No, Don't", null)
                    .show()
            }
        }


        return super.onOptionsItemSelected(item)
    }

}