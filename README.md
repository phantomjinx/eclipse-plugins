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

## Dependency Version Checker
* Plugin that searchs all workspace projects and checks their manifests for min and max versions.
* Should a manifest contain a min version but not a max version then the next major version is added as the max version.

## Update Site
To install directly into eclipse, an update site is available [here](http://phantomjinx.co.uk/org.phantomjinx.site)

## How To
 * Once installed, 2 extra buttons will be displayed on the Package Explorer Toolbar, one for Synchronizer and one for Dependency Version checker.

