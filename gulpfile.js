'use strict';

var gulp = require('gulp');
var browserify = require('browserify');
var del = require('del');
var reactify = require('reactify');
var source = require('vinyl-source-stream');
 
var paths = {
  main: 'src/main/js/main.js',
  jsx: ['src/main/js/**/*.jsx'],
  js: ['src/main/js/**/*.js'],
  target: 'src/main/resources/www/',
  bundleFile: 'bundle.js'
};
 
gulp.task('clean', function(done) {
  del([paths.target + paths.bundleFile], done);
});
 
gulp.task('js', ['clean'], function() {

    return browserify({
	    entries: [paths.main],
	    debug: false,
	    transform: [reactify]
	  })
	  .bundle()
	  .pipe(source(paths.bundleFile))
	  .pipe(gulp.dest(paths.target));

});
 
gulp.task('watch', function() {
	var sources = paths.js.concat(paths.jsx);
  gulp.watch(sources, ['js']);
});
 
gulp.task('default', ['watch', 'js']);


