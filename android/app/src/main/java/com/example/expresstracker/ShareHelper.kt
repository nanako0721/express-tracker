package com.example.expresstracker

import android.content.Context
import android.content.Intent
import android.graphics.*
import androidx.core.content.FileProvider
import java.io.File
import java.io.FileOutputStream

object ShareHelper {
    fun share(context:Context,shipment:Shipment,note:String){
        val paint=Paint(Paint.ANTI_ALIAS_FLAG).apply{color=Color.rgb(35,35,40);textSize=30f}
        val source=mutableListOf(note.ifBlank{shipment.company},"${shipment.company}  ${shipment.number}",shipment.statusText)
        shipment.events.forEach{source+="• ${it.content}";source+="  ${it.time}"};source+="由快递查询生成"
        val lines=source.flatMap{wrap(it,paint,920f)};val bitmap=Bitmap.createBitmap(1080,(120+lines.size*54).coerceAtLeast(500),Bitmap.Config.ARGB_8888);val canvas=Canvas(bitmap);canvas.drawColor(Color.WHITE);var y=70f
        lines.forEachIndexed{i,line->paint.textSize=if(i==0)44f else 30f;paint.isFakeBoldText=i==0;canvas.drawText(line,70f,y,paint);y+=54f}
        val file=File(File(context.cacheDir,"shared").apply{mkdirs()},"shipment-${shipment.id}.png");FileOutputStream(file).use{bitmap.compress(Bitmap.CompressFormat.PNG,100,it)}
        val uri=FileProvider.getUriForFile(context,"${context.packageName}.files",file);context.startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).apply{type="image/png";putExtra(Intent.EXTRA_STREAM,uri);addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)},"分享物流长图"))
    }
    private fun wrap(text:String,paint:Paint,width:Float):List<String>{if(text.isBlank())return listOf("");val out=mutableListOf<String>();var start=0;while(start<text.length){val n=paint.breakText(text,start,text.length,true,width,null).coerceAtLeast(1);out+=text.substring(start,start+n);start+=n};return out}
}
