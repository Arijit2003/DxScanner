package com.example.dxscanner.Adapter;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Rect;
import android.net.Uri;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.bumptech.glide.Glide;
import com.bumptech.glide.load.engine.DiskCacheStrategy;
import com.bumptech.glide.load.resource.bitmap.DownsampleStrategy;
import com.bumptech.glide.request.RequestOptions;
import com.example.dxscanner.R;

import java.io.File;
import java.util.ArrayList;

public class GalleryAdapter extends RecyclerView.Adapter<GalleryAdapter.GalleryViewHolder> {
    ArrayList<File> myFiles;
    Context context;
    int layout;
    public GalleryAdapter(ArrayList<File> files,Context ctx,int layout){
        this.myFiles=files;
        this.context=ctx;
        this.layout=layout;
    }
    @NonNull
    @Override
    public GalleryViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        LayoutInflater layoutInflater = LayoutInflater.from(context);
        View view = layoutInflater.inflate(layout,parent,false);
        return new GalleryViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull GalleryViewHolder holder, int position) {
//        holder.imageItem.setImageURI(Uri.parse(myFiles.get(position).getAbsolutePath()));

            Glide.with(context)
                    .load(myFiles.get(position))
                    .diskCacheStrategy(DiskCacheStrategy.AUTOMATIC)
                    .downsample(DownsampleStrategy.CENTER_INSIDE)
                    .override(150,150)
                    .placeholder(R.drawable.placeholder)
                    .into(holder.imageItem);

        

    }

    @Override
    public int getItemCount() {
        return myFiles.size();
    }

    @Override
    public void onViewRecycled(@NonNull GalleryViewHolder holder) {
        super.onViewRecycled(holder);
        holder.imageItem.setImageDrawable(null);
    }

    class GalleryViewHolder extends  RecyclerView.ViewHolder{
        ImageView imageItem;
        View itemView;
        public GalleryViewHolder(@NonNull View itemView) {
            super(itemView);
            imageItem=itemView.findViewById(R.id.imageItem);
            this.itemView=itemView;
        }
    }
}
