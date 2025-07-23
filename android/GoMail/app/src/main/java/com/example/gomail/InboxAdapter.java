package com.example.gomail;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import androidx.recyclerview.widget.RecyclerView;
import java.util.List;
//displaying a list of mailItem objects
public class InboxAdapter extends RecyclerView.Adapter<InboxAdapter.MailViewHolder> {
    //emails list
    private List<MailItem> mailList;
   //mail clicking listener
    private OnMailClickListener listener;

    //inteface to handle mail click callbacks
    public interface OnMailClickListener {
        void onMailClick(MailItem mail);
    }
    //constructor
    public InboxAdapter(List<MailItem> mailList, OnMailClickListener listener) {
        this.mailList = mailList;
        this.listener = listener;
    }
    //showing as layout of each item
    @Override
    public MailViewHolder onCreateViewHolder(ViewGroup parent, int viewType) {
        View v = LayoutInflater.from(parent.getContext()).inflate(R.layout.mail_item, parent, false);
        return new MailViewHolder(v);
    }
    //binding mail data to each item
    @Override
    public void onBindViewHolder(MailViewHolder holder, int position) {
        MailItem mail = mailList.get(position);
        holder.subject.setText(mail.subject);
        holder.from.setText(mail.from);
        holder.snippet.setText(mail.snippet);
        holder.date.setText(mail.date);
        //click handling
        holder.itemView.setOnClickListener(v -> {
            if (listener != null) {
                //passing clicked mail to listener
                listener.onMailClick(mail);
            }
        });
    }
//returning mail items number
    @Override
    public int getItemCount() {
        return mailList.size();
    }
//viewholder that holds references to each mail item views
    public static class MailViewHolder extends RecyclerView.ViewHolder {
        TextView subject, from, snippet, date;

        public MailViewHolder(View v) {
            super(v);
            //getting references
            subject = v.findViewById(R.id.textSubject);
            from = v.findViewById(R.id.textFrom);
            snippet = v.findViewById(R.id.textSnippet);
            date = v.findViewById(R.id.textDate);
        }
    }
}
