module.exports = function (showToasterAction, removeToasterAction) {
    return Reflux.createStore({

        getInitialState: function () {
            return this.state;
        },

        init: function () {
            this.state = [];

            this.listenTo(showToasterAction, this.showToasterHandler);
            this.listenTo(removeToasterAction, this.removeToasterHandler);
        },

        showToasterHandler: function (message, type = "danger") {
            this.state.push({ id: Date.now() + '-' + Math.round(Math.random() * 100000000), message, type });
            this.trigger(this.state);
        },

        removeToasterHandler: function (id) {
            const newState = this.state.filter(toaster => toaster.id !== id);
            this.trigger(newState);
        }
    });
}