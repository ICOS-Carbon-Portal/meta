'use strict';

var gulp = require('gulp');
var browserify = require('browserify');
var del = require('del');
var reactify = require('reactify');
var source = require('vinyl-source-stream');

['metaentry', 'labeling'].forEach(function(project){

	var paths = {
		main: 'src/main/js/' + project + '/main.js',
		jsx: ['src/main/js/' + project + '/**/*.jsx'],
		js: ['src/main/js/' + project + '/**/*.js'],
		target: 'src/main/resources/www/',
		bundleFile: project + '.js'
	};

	gulp.task('clean' + project, function(done) {
		del([paths.target + paths.bundleFile], done);
	});

	gulp.task('js' + project, ['clean' + project], function() {

		return browserify({
			entries: [paths.main],
			debug: false,
			transform: [reactify]
			})
			.bundle()
			.on('error', function(err){
				console.log(err);
				this.emit('end');
			})
			.pipe(source(paths.bundleFile))
			.pipe(gulp.dest(paths.target));

	});

	gulp.task('watch' + project, function() {
		var sources = paths.js.concat(paths.jsx);
		gulp.watch(sources, ['js' + project]);
	});

	gulp.task(project, ['watch' + project, 'js' + project]);

});


