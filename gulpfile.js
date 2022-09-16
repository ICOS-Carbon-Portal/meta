'use strict';

var gulp = require('gulp');
var browserify = require('browserify');
var del = require('del');
var source = require('vinyl-source-stream');
var babel = require('babelify');

var projects = ['metaentry', 'labeling', 'sparqlclient'];
projects.forEach(function(project){

	var paths = {
		main: 'src/main/js/' + project + '/main.js',
		jsx: ['src/main/js/' + project + '/**/*.jsx'],
		js: ['src/main/js/' + project + '/**/*.js'],
		common: ['src/main/js/common/**/*.js'],
		target: 'target/scala-3.2.0/classes/www/',
		bundleFile: project + '.js'
	};

	gulp.task('clean' + project, async function() {
		del([paths.target + paths.bundleFile]);
	});

	gulp.task('build' + project, function() {

		return browserify({
				entries: [paths.main],
				debug: false,
				transform: [babel]
			})
			.bundle()
			.pipe(source(paths.bundleFile))
			.pipe(gulp.dest(paths.target));

	});

	gulp.task('js' + project, gulp.series('clean' + project, 'build' + project));

	gulp.task('watch' + project, function() {
		var sources = paths.js.concat(paths.jsx, paths.common);
		gulp.watch(sources, gulp.series(['js' + project]));
	});

	gulp.task(project, gulp.series(['watch' + project, 'js' + project]));

});

gulp.task('default', gulp.series(projects.map(function(p){return 'js' + p;})));
