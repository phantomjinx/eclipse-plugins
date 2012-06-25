# eclipse-plugins
Contains a set of plugins that make Eclipse development just that little bit easier

## Project Synchronizer
* Plugin that locates a refresh button on the Package Explorer toolbar.
* Click the button to refresh all projectes in the workspace from a selected parent directory.
* Useful to updating a plugin repository if it has just been updated via a version control mechanism, eg. git
* Deletes invalid projects
* Adds new projects from the selected directory
* Moves all projects into sensible working sets based on the parent directories of the projects.
  ** A typical RCP repository will have features and plugins directories so the workspace will get similar working sets

## Update Site
To install directly into eclipse, an update site is available [here](http://phantomjinx.co.uk/org.phantomjinx.site)

