'use strict';
var path = require('path');
var util = require('util');

var _ = require('lodash');
var Generator = require('yeoman-generator');
var moment = require('moment');

module.exports = class extends Generator {

  constructor(args, opts) {
    super(args, opts);
    //this is optional
    this.argument('namespace', { type: String, required: false, optional: true, desc: 'namespace for the application'});
  }

  prompting () {
    return this.prompt([
      {
        type: 'input',
        name: 'appName',
        message: 'Application Name',
        default: path.basename(process.cwd())
      },
      {
        type: 'input',
        name: 'namespace',
        message: 'Application Namespace: starts with au.com',
        default: this.namespace
      },
      {
        type: 'input',
        name: 'processorName',
        message: 'Stream Processor Name',
        default: defaultEmail
      }
    ]).then((answers) => {
      this.appName = _.camelCase(answers.appName);
      this.processorName = _.kebabCase(answers.processorName);
      this.namespace = answers.namespace.toLowerCase;
      this.log('   Application: %s -> %s', this.namespace, this.appName);
      this.log('   Processor Name:      %s', this.processorName);
    });
  }

  paths () {
    this.log('Confirm your project path: %s', this.destinationPath());
    //will use it later
    //var now = moment();

    var model = {
      appName: this.appName,
      processorName: this.processorName,
      namespace: this.namespace
    };

    
    this.fs.copyTpl(
      this._from('_gitignore'),
      this._to('.gitignore'),
      model
    );
    this.fs.copyTpl(
      this._from('_readme.md'),
      this._to('readme.md'),
      model
    );
    this.fs.copyTpl(
      this._from('_pom.xml'),
      this._to('pom.xml'),
      model
    );   
    // copy all java files
    this.fs.copyTpl(
      this._from('**/*.java'),
      this._to(),
      model
    );    
  }

  //contextroot
  _from(filename) {
    return this.templatePath(filename);
  }

  _to(filename) {
    if (filename) {
      return this.destinationPath(filename);
    }
    return this.destinationRoot();
  }  
};