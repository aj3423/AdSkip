# What is it
An Android app that leverages Accessibility Services to automatically skip ads by tapping the skip/close buttons, for example:

<p>
  <img width="200" height="400" alt="1" src="https://github.com/user-attachments/assets/40ce3c86-ff0e-4962-a288-9804b01b869d" />
  <img width="200" height="400" alt="2" src="https://github.com/user-attachments/assets/7f263513-8905-435a-890c-2cabdf5fbab7" />
</p>


Usage
=================
* [1. Grant accessibility permission](#1-grant-accessibility-permission)
* [2. Capture a Snapshot](#2-capture-a-snapshot)
* [3. Create a Rule from the Snapshot](#3-create-a-rule-from-the-snapshot)
* [4. Save the rule](#4-save-the-rule)


### 1. Grant accessibility permission

<img width="250" height="500" alt="settings" src="https://github.com/user-attachments/assets/3c05b3f3-1a29-4409-bad2-987a4b323d3a" />

### 2. Capture a Snapshot
When an ad appears, press both **Volume Up + Volume Down** to capture a snapshot, it will appear in the **Snapshots** tab.

  <img width="250" height="500" alt="snapshots" src="https://github.com/user-attachments/assets/2c2854d4-0a2e-4358-8d0a-77fa0312fa8c" />

### 3. Create a Rule from the Snapshot
  **Open** the snapshot, the screen will split into three panes:
  
  <img width="800" height="400" alt="snapshot_editing" src="https://github.com/user-attachments/assets/7fbd4309-c5f0-4f66-9ae8-1f8a9ff4a60e" />
  
  - Left pane: Tap on the screenshot to select the "close" or "skip" button (**long press** to zoom in).
  - Middle pane: Shows the full window hierarchy. The selected item is automatically focused.
  - Right pane: The app automatically tries to find the unique path of the selected element. You can also manually edit it.

### 4. Save the rule
  If a unique path is found, tap **Save Rule** in the right pane, it will appear in the Rules tab and become active immediately.
  
  <img width="250" height="500" alt="rules" src="https://github.com/user-attachments/assets/e8679230-5a5a-4e5d-b8a5-3cdde5c899d1" />
