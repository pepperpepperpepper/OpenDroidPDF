package org.opendroidpdf;

import java.io.File;
import java.util.ArrayList;
import java.util.LinkedList;

import androidx.fragment.app.ListFragment;
import android.view.View;
import android.view.ViewGroup;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.widget.ListView;
import android.content.res.Resources;
import android.content.Context;
import android.widget.ArrayAdapter;
import android.app.Activity;
import android.app.Application;
import android.widget.TextView;
import android.widget.ImageView;
import android.net.Uri;
import android.content.Intent;

import org.opendroidpdf.app.AppServices;
import org.opendroidpdf.app.services.recent.RecentEntry;
import org.opendroidpdf.app.services.recent.RecentFilesStore;

public class RecentFilesFragment extends ListFragment {

    public interface goToDirInterface {
        public void goToDir(File dir);
    }
    
    private enum Purpose { ChooseFileForOpening, PickKeyFile, ChooseFileForSaving, ChooseFileForOpeningAndLaunch }
    
    private ArrayAdapter<String> mRecentFilesAdapter;
    private Purpose mPurpose;

    static final String PURPOSE = "purpose";
    static final String FILENAME = "filename";
    static final String DIRECTORY = "directory";

    private int numDirectories = 0;
    private RecentFilesStore recentFilesStore;
    
    public static final RecentFilesFragment newInstance(Intent intent) {
            //Collect data from intent
        Purpose purpose;
        if(intent.ACTION_MAIN.equals(intent.getAction()))
            purpose = Purpose.ChooseFileForOpeningAndLaunch;
        else if(intent.ACTION_EDIT.equals(intent.getAction()))
            purpose = Purpose.ChooseFileForOpening;
        else if((intent.ACTION_PICK.equals(intent.getAction())))
            purpose = Purpose.ChooseFileForSaving;
        else
            purpose = Purpose.PickKeyFile;
        
            //Put the collected data in a Bundle
        Bundle bundle = new Bundle(3);
        bundle.putString(PURPOSE,purpose.toString());
        
        RecentFilesFragment recentFilesFragment = new RecentFilesFragment();
        recentFilesFragment.setArguments(bundle);
        return recentFilesFragment;
    }
    

    @Override
    public void onSaveInstanceState(Bundle bundle) {
        super.onSaveInstanceState(bundle);
        bundle.putString(PURPOSE,mPurpose.toString());
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        
            //Retrieve the data that was set with setArguments()
        if(getArguments()!=null)
            mPurpose = Purpose.valueOf(getArguments().getString(PURPOSE));
        else if(savedInstanceState != null)
            mPurpose = Purpose.valueOf(savedInstanceState.getString(PURPOSE));
    }  

    @Override
    public void onResume() {
        super.onResume();
        loadRecentFilesList();
    }


    @Override
    public void onPause() {
        super.onPause();
    }

    private RecentFilesStore getRecentFilesStore() {
        if (recentFilesStore != null) return recentFilesStore;
        if (getActivity() == null) return null;
        recentFilesStore = AppServices.init((Application) getActivity().getApplication()).recentFilesStore();
        return recentFilesStore;
    }

    
    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        super.onCreateView(inflater, container, savedInstanceState);

        final LayoutInflater layoutInflater = inflater; //used to pass on the inflator to the Adapter
            //Create the RecentFilesAdapter (an ArrayListAdapter)
        mRecentFilesAdapter = new ArrayAdapter<String>(getActivity(), android.R.layout.simple_list_item_1) {
            @Override
            public View getView(int position, View convertView, ViewGroup parent) {                
                View view;
                if (convertView == null) {
                    view = layoutInflater.inflate(R.layout.picker_entry, null);
                } else {
                    view = convertView;
                }
				String recentFileString = null;
				Uri recentFileUri = Uri.parse(getItem(position));
				if(recentFileUri != null)
				{
					File recentFile = new File(Uri.decode(recentFileUri.getEncodedPath()));
					if(recentFile != null)
						recentFileString = recentFile.getAbsolutePath();
					else
						recentFileString = recentFileString;
				}
				else
					recentFileString = recentFileString;
				((TextView)view.findViewById(R.id.name)).setText(recentFileString);
					
                if(position < numDirectories)
                    ((ImageView)view.findViewById(R.id.icon)).setImageResource(R.drawable.ic_dir);  
                else
                    ((ImageView)view.findViewById(R.id.icon)).setImageResource(R.drawable.ic_doc);
                return view;
            }
        };
        
        loadRecentFilesList();
            
            // Inflate the layout for this fragment
        View rootView = inflater.inflate(R.layout.recentfiles, container, false);
        setListAdapter(mRecentFilesAdapter);  
        return rootView;
    }


    @Override
    public void onListItemClick(ListView l, View v, int position, long id) {
        super.onListItemClick(l, v, position, id);

        Uri uri = Uri.parse(mRecentFilesAdapter.getItem(position));
        
        if (mRecentFilesAdapter == null ) return;

            //A directory was clicked and we are in pick a file mode
        if(position < numDirectories)
        {
            ((goToDirInterface)getActivity()).goToDir(new File(uri.getPath()));
            return;
        }

        Intent intent = new Intent(getActivity(),OpenDroidPDFActivity.class);
        intent.setAction(Intent.ACTION_VIEW);
        intent.setData(uri);
        switch (mPurpose) {
            case ChooseFileForOpeningAndLaunch:
                intent.setAction(Intent.ACTION_VIEW);
                startActivity(intent);
                getActivity().finish();
                break;
            case ChooseFileForOpening:
            case ChooseFileForSaving:
            case PickKeyFile:
				getActivity().setResult(Activity.RESULT_OK, intent);
                getActivity().finish();
                break;
        }
    }

    
    private void loadRecentFilesList() {
        if (getActivity() == null || mRecentFilesAdapter == null) return;

        ArrayList<String> items = new ArrayList<>();
        RecentFilesStore store = getRecentFilesStore();
        if (store != null) {
            java.util.List<RecentEntry> recents = store.loadRecents();

                //Add the directories of the most recent files to the list if we were asked to pick a file
            LinkedList<String> recentDirectoriesList = new LinkedList<>();
            for (RecentEntry entry : recents) {
                if (entry == null) continue;
                String uriString = entry.uriString();
                if (uriString == null) continue;
                Uri recentFileUri = Uri.parse(uriString);
                File recentFileFile = new File(Uri.decode(recentFileUri.getEncodedPath()));
                if (recentFileFile != null) {
                    String absolutePath = recentFileFile.getAbsolutePath();
                    if (absolutePath != null) {
                        int lastSlash = absolutePath.lastIndexOf("/");
                        if (lastSlash > 0) {
                            String dir = absolutePath.substring(0, lastSlash);
                            recentDirectoriesList.remove(dir);
                            recentDirectoriesList.addFirst(dir);
                        }
                    }
                }
            }
            numDirectories = recentDirectoriesList.size();
            items.addAll(recentDirectoriesList);
            for (RecentEntry entry : recents) {
                if (entry == null || entry.uriString() == null) continue;
                items.add(entry.uriString());
            }
        } else {
            numDirectories = 0;
        }

            //Update the data in the adapter
        mRecentFilesAdapter.clear();
        mRecentFilesAdapter.addAll(items);
        mRecentFilesAdapter.notifyDataSetChanged();
    }

    private void setTitle() {
        Activity activity = getActivity(); 
        if (isAdded() && activity != null) {
            Resources res = getResources();
            String recentTitle = res.getString(R.string.recent);
            activity.setTitle(recentTitle);
        }
    }

    public void inForground() {
        setTitle();
        loadRecentFilesList();
    }
}
